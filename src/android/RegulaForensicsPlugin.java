package com.geekay.plugin.regula;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Base64;
import android.util.Log;
import android.util.Size;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.regula.facesdk.FaceSDK;
import com.regula.facesdk.callback.FaceInitializationCompletion;
import com.regula.facesdk.configuration.FaceCaptureConfiguration;
import com.regula.facesdk.configuration.InitializationConfiguration;
import com.regula.facesdk.configuration.LivenessConfiguration;
import com.regula.facesdk.enums.ImageType;
import com.regula.facesdk.enums.LivenessStatus;
import com.regula.facesdk.model.MatchFacesImage;
import com.regula.facesdk.model.results.matchfaces.MatchFacesSimilarityThresholdSplit;
import com.regula.facesdk.request.MatchFacesRequest;

// DetectFaces imports
import com.regula.facesdk.request.DetectFacesRequest;
import com.regula.facesdk.configuration.DetectFacesConfiguration;
import com.regula.facesdk.configuration.OutputImageCrop;
import com.regula.facesdk.configuration.OutputImageParams;
import com.regula.facesdk.enums.OutputImageCropAspectRatio;
import com.regula.facesdk.model.results.detectfaces.DetectFacesResponse;
import com.regula.facesdk.model.results.detectfaces.DetectFaceResult;

/**
 * RegulaForensicsPlugin — Cordova plugin wrapping the Regula Face SDK.
 *
 * Action routing:
 * initializeFaceSDK — Step 1 : init SDK, optionally with a license file
 * (base64) from the frontend
 * startLiveness — Step 2 : run the Regula liveness check
 * detectFace — Step 3 : alias of startLiveness (no distinct Android mobile
 * detection API)
 * startFaceCapture — Step 4 : open Regula's face-capture camera activity
 * matchFaces — Step 5 : compare two images (both supplied as base64 from the
 * frontend)
 * deinitializeFaceSDK — housekeeping : stop SDK
 */
public class RegulaForensicsPlugin extends CordovaPlugin {

    private static final String TAG = "RegulaPlugin";

    // -------------------------------------------------------------------------
    // Action router
    // -------------------------------------------------------------------------

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {

        switch (action) {

            case "initializeFaceSDK":
                String licenseBase64 = args.isNull(0) ? null : args.getString(0).trim();
                if (licenseBase64 != null && licenseBase64.isEmpty())
                    licenseBase64 = null;
                initializeFaceSDK(licenseBase64, callbackContext);
                return true;
            case "startLiveness":
                startLiveness(callbackContext);
                return true;
            case "detectFace":
                detectFace(callbackContext);
                return true;
            case "startFaceCapture":
                startFaceCapture(callbackContext);
                return true;

            case "matchFaces":
                matchFaces(args.getJSONArray(0), callbackContext);
                return true;
            case "deinitializeFaceSDK":
                deinitializeFaceSDK(callbackContext);
                return true;
        }

        return false;
    }

    // =========================================================================
    // STEP 1 — Initialize
    // =========================================================================

    /**
     * Initialises the Face SDK.
     *
     * JS contract:
     * plugin.initializeFaceSDK(licenseBase64 | null, successCb, errorCb)
     *
     * @param licenseBase64 base64-encoded contents of a regula.license file,
     *                      or null to initialise in online/basic mode.
     */
    private void initializeFaceSDK(String licenseBase64, CallbackContext callbackContext) {
        Context context = cordova.getContext();

        FaceInitializationCompletion completion = (status, exception) -> {
            if (status) {
                String mode = (licenseBase64 == null) ? "Online/Basic" : "Offline/Licensed";
                Log.d(TAG, "SDK initialized — mode: " + mode);
                try {
                    JSONObject result = new JSONObject();
                    result.put("success", true);
                    result.put("message", "Face SDK initialized (" + mode + ").");
                    callbackContext.success(result);
                } catch (JSONException e) {
                    callbackContext.success("Face SDK initialized (" + mode + ").");
                }
            } else {
                String msg = buildInitErrorMessage(exception, licenseBase64 == null);
                Log.e(TAG, "SDK init failed: " + msg);
                if (exception != null) {
                    StringWriter sw = new StringWriter();
                    exception.printStackTrace(new PrintWriter(sw));
                    Log.e(TAG, "Init exception trace: " + sw);
                }
                callbackContext.error(msg);
            }
        };
        if (licenseBase64 == null) {
            FaceSDK.Instance().initialize(context, completion);
        } else {
            try {
                byte[] licenseBytes = Base64.decode(licenseBase64, Base64.DEFAULT);
                InitializationConfiguration configuration = new InitializationConfiguration.Builder(licenseBytes)
                        .build();
                FaceSDK.Instance().initialize(context, configuration, completion);
            } catch (IllegalArgumentException e) {
                callbackContext.error("Invalid license file: " + e.getMessage());
            }
        }
    }

    /**
     * Builds a human-readable init error from the SDK exception.
     * The SDK's exception.getMessage() frequently returns null, so we use
     * reflection / toString() to surface whatever info is available.
     */
    private String buildInitErrorMessage(Exception exception, boolean isOnlineMode) {
        if (exception == null) {
            return "SDK initialization failed (unknown reason). " +
                    (isOnlineMode ? "Check internet connection." : "Check license file validity.");
        }

        // Try getMessage() first
        String msg = exception.getMessage();

        // If null, try to get errorCode via reflection (Regula uses getErrorCode())
        if (msg == null || msg.isEmpty()) {
            try {
                java.lang.reflect.Method m = exception.getClass().getMethod("getErrorCode");
                Object code = m.invoke(exception);
                if (code != null) {
                    msg = "Init error code: " + code + " (" + exception.getClass().getSimpleName() + ")";
                }
            } catch (Exception ignored) {
                /* reflection not available */ }
        }

        // Final fallback
        if (msg == null || msg.isEmpty()) {
            msg = exception.getClass().getSimpleName() + ": " +
                    (exception.toString().length() > 120
                            ? exception.toString().substring(0, 120) + "…"
                            : exception.toString());
        }

        // Add hint based on mode
        if (isOnlineMode) {
            msg += " | HINT: Online mode requires internet. Ensure device can reach Regula licensing servers.";
        } else {
            msg += " | HINT: Verify the .license file is valid and not expired.";
        }

        return msg;
    }

    // =========================================================================
    // STEP 2 / STEP 3 — Liveness / Face Detection
    // =========================================================================

    /**
     * Starts the Regula liveness check.
     * Also used as the "detectFace" action since Android mobile SDK maps
     * face detection through the liveness response.
     *
     * JS contract:
     * plugin.startLiveness(successCb, errorCb)
     * plugin.detectFace(successCb, errorCb)
     *
     * Success payload:
     * { liveness: 0|1, image: "<base64 PNG>", error?: "<message>" }
     * liveness: 1 = PASSED, 0 = not passed / unknown
     */
    private void startLiveness(CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            LivenessConfiguration configuration = new LivenessConfiguration.Builder().build();

            FaceSDK.Instance().startLiveness(activity, configuration, livenessResponse -> {
                try {
                    JSONObject result = new JSONObject();

                    if (livenessResponse.getException() != null) {
                        String errMsg = livenessResponse.getException().getMessage();
                        Log.w(TAG, "Liveness exception: " + errMsg);
                        result.put("error", errMsg);
                        result.put("liveness", 0);
                        callbackContext.success(result);
                        return;
                    }

                    int livenessInt = (livenessResponse.getLiveness() == LivenessStatus.PASSED) ? 1 : 0;
                    result.put("liveness", livenessInt);

                    Bitmap bmp = livenessResponse.getBitmap();
                    if (bmp != null) {
                        result.put("image", bitmapToBase64(bmp));
                    }

                    callbackContext.success(result);

                } catch (Exception e) {
                    Log.e(TAG, "Liveness result error: " + e.getMessage());
                    callbackContext.error(e.getMessage());
                }
            });
        });
    }

    // =========================================================================
    // STEP 3 — Face Detection
    // =========================================================================

    /**
     * Starts the Regula face detection on a provided image.
     *
     * JS contract:
     * plugin.detectFace([ "<base64 PNG/JPG>" ], successCb, errorCb)
     */
    private void detectFace(JSONArray args, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                if (args.length() == 0 || args.isNull(0)) {
                    callbackContext.error("detectFace requires a base64 image string as the first argument.");
                    return;
                }

                String base64Str = args.getString(0);

                // Strip data-URI prefix if present
                int commaIdx = base64Str.indexOf(',');
                if (commaIdx >= 0) {
                    base64Str = base64Str.substring(commaIdx + 1);
                }

                // Decode base64 to Bitmap
                byte[] imageBytes = Base64.decode(base64Str, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                if (bitmap == null) {
                    callbackContext.error("Failed to decode image. Ensure it is a valid base64 string.");
                    return;
                }

                // Setup configuration according to Regula Docs
                DetectFacesConfiguration configuration = new DetectFacesConfiguration();
                configuration.setOnlyCentralFace(true);

                // Configure requested output image format/sizing
                OutputImageCrop outputImageCrop = new OutputImageCrop(
                        OutputImageCropAspectRatio.OUTPUT_IMAGE_CROP_ASPECT_RATIO_2X3,
                        new Size(500, 750),
                        Color.WHITE,
                        true);

                OutputImageParams outputImageParams = new OutputImageParams(outputImageCrop, Color.WHITE);
                configuration.setOutputImageParams(outputImageParams);

                // Create the request
                DetectFacesRequest request = new DetectFacesRequest(bitmap, configuration);
                request.setTag(UUID.randomUUID().toString());

                // Perform detection on the main thread (SDK constraint)
                Activity activity = cordova.getActivity();
                activity.runOnUiThread(() -> {
                    FaceSDK.Instance().detectFaces(request, detectFacesResponse -> {
                        try {
                            JSONObject result = new JSONObject();

                            if (detectFacesResponse.getError() != null) {
                                String errMsg = detectFacesResponse.getError().getMessage();
                                Log.w(TAG, "Detect faces exception: " + errMsg);
                                result.put("error", errMsg);
                                callbackContext.success(result);
                                return;
                            }

                            JSONArray detectionsArray = new JSONArray();
                            List<DetectFaceResult> detections = detectFacesResponse.getAllDetections();

                            if (detections != null) {
                                for (DetectFaceResult detectFaceResult : detections) {
                                    JSONObject faceObj = new JSONObject();

                                    faceObj.put("qualityCompliant", detectFaceResult.isQualityCompliant());

                                    // Safely convert cropped face graphic to base64 payload
                                    Object cropImgObj = detectFaceResult.getCropImage();
                                    if (cropImgObj instanceof Bitmap) {
                                        faceObj.put("cropImage", bitmapToBase64((Bitmap) cropImgObj));
                                    } else if (cropImgObj != null) {
                                        // Some wrapper versions return encoded strings immediately
                                        faceObj.put("cropImage", cropImgObj.toString());
                                    }

                                    // Extract Bounding Box
                                    Rect faceRect = detectFaceResult.getFaceRect();
                                    if (faceRect != null) {
                                        JSONObject rectObj = new JSONObject();
                                        rectObj.put("left", faceRect.left);
                                        rectObj.put("top", faceRect.top);
                                        rectObj.put("right", faceRect.right);
                                        rectObj.put("bottom", faceRect.bottom);
                                        faceObj.put("faceRect", rectObj);
                                    }

                                    // Extract Landmarks
                                    List<Point> landMarks = detectFaceResult.getLandMarks();
                                    if (landMarks != null && !landMarks.isEmpty()) {
                                        JSONArray landmarksArray = new JSONArray();
                                        for (Point p : landMarks) {
                                            JSONObject pointObj = new JSONObject();
                                            pointObj.put("x", p.x);
                                            pointObj.put("y", p.y);
                                            landmarksArray.put(pointObj);
                                        }
                                        faceObj.put("landmarks", landmarksArray);
                                    }

                                    detectionsArray.put(faceObj);
                                }
                            }

                            result.put("scenario", detectFacesResponse.getScenario());
                            result.put("detections", detectionsArray);

                            callbackContext.success(result);

                        } catch (Exception e) {
                            Log.e(TAG, "Detect faces result error: " + e.getMessage());
                            callbackContext.error(e.getMessage());
                        }
                    });
                });

            } catch (Exception e) {
                Log.e(TAG, "detectFace setup error: " + e.getMessage());
                callbackContext.error("Failed setting up face detection: " + e.getMessage());
            }
        });
    }

    // =========================================================================
    // STEP 4 — Face Capture (Regula camera activity)
    // =========================================================================

    /**
     * Opens the Regula face-capture camera activity.
     *
     * JS contract:
     * plugin.startFaceCapture(successCb, errorCb)
     *
     * Success payload:
     * { image: "<base64 PNG>", error?: "<message>" }
     */
    private void startFaceCapture(CallbackContext callbackContext) {
        Activity activity = cordova.getActivity();

        activity.runOnUiThread(() -> {
            FaceCaptureConfiguration configuration = new FaceCaptureConfiguration.Builder()
                    .setCameraId(0) // rear camera
                    .setCameraSwitchEnabled(true) // allow switching to front
                    .build();

            FaceSDK.Instance().presentFaceCaptureActivity(activity, configuration, faceCaptureResponse -> {
                try {
                    JSONObject result = new JSONObject();

                    if (faceCaptureResponse.getException() != null) {
                        String errMsg = faceCaptureResponse.getException().getMessage();
                        Log.w(TAG, "Face capture exception: " + errMsg);
                        result.put("error", errMsg);
                        callbackContext.success(result);
                        return;
                    }

                    if (faceCaptureResponse.getImage() != null
                            && faceCaptureResponse.getImage().getBitmap() != null) {
                        Bitmap bmp = faceCaptureResponse.getImage().getBitmap();
                        result.put("image", bitmapToBase64(bmp));
                        result.put("imageType", 1); // PRINTED
                    } else {
                        result.put("error", "No face image returned.");
                    }

                    callbackContext.success(result);

                } catch (Exception e) {
                    Log.e(TAG, "Face capture result error: " + e.getMessage());
                    callbackContext.error(e.getMessage());
                }
            });
        });
    }

    // =========================================================================
    // STEP 5 — Face Match
    // =========================================================================

    /**
     * Compares two face images sent as base64 strings from the frontend.
     * The frontend supplies both images (from camera, gallery or file picker).
     *
     * JS contract:
     * plugin.matchFaces(
     * [ { base64: "<string>", imageType: 1 },
     * { base64: "<string>", imageType: 1 } ],
     * successCb, errorCb
     * )
     *
     * Success payload:
     * { similarity: 0.0–1.0, matched: true|false, error?: "<message>" }
     */
    private void matchFaces(JSONArray imagesJson, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                if (imagesJson.length() < 2) {
                    callbackContext.error("matchFaces requires exactly 2 images.");
                    return;
                }

                List<MatchFacesImage> imageList = new ArrayList<>();

                for (int i = 0; i < 2; i++) {
                    JSONObject imgObj = imagesJson.getJSONObject(i);

                    if (imgObj.isNull("base64") || imgObj.getString("base64").isEmpty()) {
                        callbackContext.error("Image at index " + i + " is missing 'base64'.");
                        return;
                    }

                    String base64Str = imgObj.getString("base64");

                    // Strip data-URI prefix if present (e.g. "data:image/png;base64,...")
                    int commaIdx = base64Str.indexOf(',');
                    if (commaIdx >= 0) {
                        base64Str = base64Str.substring(commaIdx + 1);
                    }

                    byte[] imageBytes = Base64.decode(base64Str, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

                    if (bitmap == null) {
                        callbackContext
                                .error("Failed to decode image at index " + i + ". Ensure it is a valid PNG/JPEG.");
                        return;
                    }

                    int imageTypeInt = imgObj.optInt("imageType", 1);
                    ImageType imageType = intToImageType(imageTypeInt);

                    imageList.add(new MatchFacesImage(bitmap, imageType));
                }

                MatchFacesRequest request = new MatchFacesRequest(imageList);

                FaceSDK.Instance().matchFaces(cordova.getContext(), request, matchFacesResponse -> {
                    try {
                        JSONObject result = new JSONObject();

                        if (matchFacesResponse.getException() != null) {
                            String errMsg = matchFacesResponse.getException().getMessage();
                            Log.w(TAG, "Match exception: " + errMsg);
                            result.put("error", errMsg);
                            callbackContext.success(result);
                            return;
                        }

                        if (matchFacesResponse.getResults() == null
                                || matchFacesResponse.getResults().isEmpty()) {
                            result.put("error", "No face detected in one or both images.");
                            callbackContext.success(result);
                            return;
                        }

                        MatchFacesSimilarityThresholdSplit split = new MatchFacesSimilarityThresholdSplit(
                                matchFacesResponse.getResults(), 0.75d);

                        boolean matched = !split.getMatchedFaces().isEmpty();
                        Double similarity;

                        if (matched) {
                            similarity = split.getMatchedFaces().get(0).getSimilarity();
                        } else if (!split.getUnmatchedFaces().isEmpty()) {
                            similarity = split.getUnmatchedFaces().get(0).getSimilarity();
                        } else {
                            similarity = null;
                        }

                        result.put("matched", matched);
                        result.put("similarity", similarity != null ? similarity : JSONObject.NULL);

                        Log.d(TAG, "Match result — similarity: " + similarity + ", matched: " + matched);
                        callbackContext.success(result);

                    } catch (Exception e) {
                        Log.e(TAG, "Match result error: " + e.getMessage());
                        callbackContext.error(e.getMessage());
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "matchFaces setup error: " + e.getMessage());
                callbackContext.error(e.getMessage());
            }
        });
    }

    // =========================================================================
    // Housekeeping — Deinitialize
    // =========================================================================

    private void deinitializeFaceSDK(CallbackContext callbackContext) {
        FaceSDK.Instance().deinitialize();
        Log.d(TAG, "SDK deinitialized.");
        callbackContext.success("Face SDK deinitialized.");
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Maps an integer imageType value from JavaScript to the SDK's ImageType enum.
     * Handles both old-style (PRINTED=1, LIVE=2) and the documented integer values.
     */
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

    /** Encode a Bitmap to a base64 PNG string (no line-wrapping). */
    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
