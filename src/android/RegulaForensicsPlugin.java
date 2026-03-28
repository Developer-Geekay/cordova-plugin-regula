package com.geekay.plugin.regula;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.regula.facesdk.FaceSDK;
import com.regula.facesdk.callback.FaceInitializationCompletion;
import com.regula.facesdk.configuration.InitializationConfiguration;
import com.regula.facesdk.enums.ImageType;
import com.regula.facesdk.model.MatchFacesImage;
import com.regula.facesdk.model.results.matchfaces.MatchFacesSimilarityThresholdSplit;
import com.regula.facesdk.request.MatchFacesRequest;

public class RegulaForensicsPlugin extends CordovaPlugin {

    // In-memory store: imageId -> Bitmap
    // Avoids passing large base64 blobs over the Cordova bridge for matchFaces
    private final Map<String, Bitmap> capturedBitmaps = new HashMap<>();

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "initializeFaceSDK":
                String licenseBase64 = args.isNull(0) ? null : args.getString(0);
                this.initializeFaceSDK(licenseBase64, callbackContext);
                return true;
            case "deinitializeFaceSDK":
                this.deinitializeFaceSDK(callbackContext);
                return true;
            case "startLiveness":
                this.startLiveness(callbackContext);
                return true;
            case "startFaceCapture":
                this.startFaceCapture(callbackContext);
                return true;
            case "matchFaces":
                // Only lightweight IDs are passed — no base64 crosses the bridge here
                this.matchFaces(args.getJSONArray(0), callbackContext);
                return true;
            case "clearCapturedImages":
                capturedBitmaps.clear();
                callbackContext.success("Cleared.");
                return true;
        }
        return false;
    }

    private void initializeFaceSDK(String licenseBase64, CallbackContext callbackContext) {
        Context context = cordova.getContext();

        FaceInitializationCompletion completion = (status, exception) -> {
            if (status) {
                String coreType = (licenseBase64 == null || licenseBase64.isEmpty()) ? "Basic" : "Match";
                callbackContext.success("Face SDK (Core " + coreType + ") initialized successfully.");
            } else {
                String msg = (exception != null) ? exception.getMessage() : "Unknown initialization error";
                callbackContext.error(msg);
            }
        };

        if (licenseBase64 == null || licenseBase64.isEmpty()) {
            FaceSDK.Instance().initialize(context, completion);
        } else {
            byte[] license = Base64.decode(licenseBase64, Base64.DEFAULT);
            InitializationConfiguration configuration = new InitializationConfiguration.Builder(license).build();
            FaceSDK.Instance().initialize(context, configuration, completion);
        }
    }

    private void deinitializeFaceSDK(CallbackContext callbackContext) {
        FaceSDK.Instance().deinitialize();
        capturedBitmaps.clear();
        callbackContext.success("Face SDK deinitialized.");
    }

    private void startLiveness(CallbackContext callbackContext) {
        cordova.getActivity()
                .runOnUiThread(() -> FaceSDK.Instance().startLiveness(cordova.getContext(), livenessResponse -> {
                    try {
                        JSONObject result = new JSONObject();
                        if (livenessResponse.getException() != null) {
                            result.put("error", livenessResponse.getException().getMessage());
                            callbackContext.success(result);
                            return;
                        }
                        int livenessStatus = (livenessResponse
                                .getLiveness() == com.regula.facesdk.enums.LivenessStatus.PASSED) ? 1 : 0;
                        result.put("liveness", livenessStatus);

                        if (livenessResponse.getBitmap() != null) {
                            String imageId = storeBitmap(livenessResponse.getBitmap());
                            result.put("imageId", imageId);
                            result.put("imageType", 2); // LIVE
                            result.put("image", bitmapToBase64(livenessResponse.getBitmap()));
                        }
                        callbackContext.success(result);
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                }));
    }

    private void startFaceCapture(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(
                () -> FaceSDK.Instance().presentFaceCaptureActivity(cordova.getActivity(), faceCaptureResponse -> {
                    try {
                        JSONObject result = new JSONObject();
                        if (faceCaptureResponse.getException() != null) {
                            result.put("error", faceCaptureResponse.getException().getMessage());
                            callbackContext.success(result);
                            return;
                        }
                        if (faceCaptureResponse.getImage() != null
                                && faceCaptureResponse.getImage().getBitmap() != null) {
                            Bitmap bmp = faceCaptureResponse.getImage().getBitmap();
                            String imageId = storeBitmap(bmp);
                            result.put("imageId", imageId);
                            result.put("imageType", 1); // PRINTED
                            result.put("image", bitmapToBase64(bmp));
                        }
                        callbackContext.success(result);
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                }));
    }

    private void matchFaces(JSONArray imagesJson, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                List<MatchFacesImage> imageList = new ArrayList<>();
                for (int i = 0; i < imagesJson.length(); i++) {
                    JSONObject imgObj = imagesJson.getJSONObject(i);
                    int imageTypeInt = imgObj.optInt("imageType", 1);
                    ImageType imageType = intToImageType(imageTypeInt);

                    Bitmap bitmap = null;

                    // Prefer imageId lookup (no bridge data transfer)
                    if (!imgObj.isNull("imageId")) {
                        String imageId = imgObj.getString("imageId");
                        bitmap = capturedBitmaps.get(imageId);
                        if (bitmap == null) {
                            callbackContext.error("Image not found for imageId: " + imageId + ". Please recapture.");
                            return;
                        }
                    } else if (!imgObj.isNull("base64")) {
                        // Fallback: decode from base64 if imageId not available
                        String base64 = imgObj.getString("base64");
                        byte[] decodedBytes = Base64.decode(base64, Base64.DEFAULT);
                        bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                        if (bitmap == null) {
                            callbackContext.error("Failed to decode image at index " + i);
                            return;
                        }
                    } else {
                        callbackContext.error("Image at index " + i + " must have imageId or base64.");
                        return;
                    }

                    imageList.add(new MatchFacesImage(bitmap, imageType, true));
                }

                MatchFacesRequest matchRequest = new MatchFacesRequest(imageList);

                FaceSDK.Instance().matchFaces(cordova.getContext(), matchRequest, matchFacesResponse -> {
                    try {
                        JSONObject result = new JSONObject();
                        if (matchFacesResponse.getException() != null) {
                            result.put("error", matchFacesResponse.getException().getMessage());
                            callbackContext.success(result);
                            return;
                        }

                        if (matchFacesResponse.getResults() == null || matchFacesResponse.getResults().isEmpty()) {
                            result.put("error", "No face detected in one or both images. Please recapture.");
                            callbackContext.success(result);
                            return;
                        }

                        MatchFacesSimilarityThresholdSplit split = new MatchFacesSimilarityThresholdSplit(
                                matchFacesResponse.getResults(), 0.75d);

                        Double similarity = null;
                        if (!split.getMatchedFaces().isEmpty()) {
                            similarity = split.getMatchedFaces().get(0).getSimilarity();
                        } else if (!split.getUnmatchedFaces().isEmpty()) {
                            similarity = split.getUnmatchedFaces().get(0).getSimilarity();
                        }

                        result.put("similarity", similarity != null ? similarity : JSONObject.NULL);
                        callbackContext.success(result);
                    } catch (Exception e) {
                        callbackContext.error(e.getMessage());
                    }
                });
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
        });
    }

    // Stores bitmap and returns a unique ID
    private String storeBitmap(Bitmap bitmap) {
        String id = UUID.randomUUID().toString();
        capturedBitmaps.put(id, bitmap);
        return id;
    }

    private ImageType intToImageType(int type) {
        switch (type) {
            case 2:
                return ImageType.LIVE;
            case 3:
                return ImageType.RFID;
            case 4:
                return ImageType.EXTERNAL;
            case 5:
                return ImageType.DOCUMENT_WITH_LIVE;
            case 6:
                return ImageType.GHOST_PORTRAIT;
            case 1:
            default:
                return ImageType.PRINTED;
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
}
