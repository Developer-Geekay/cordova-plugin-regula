package cordova.plugin.regula;

import android.content.Context;
import android.util.Base64;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;

import com.regula.facesdk.FaceSDK;
import com.regula.facesdk.callback.FaceInitializationCompletion;
import com.regula.facesdk.configuration.InitializationConfiguration;

public class RegulaForensicsPlugin extends CordovaPlugin {

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
        callbackContext.success("Face SDK deinitialized.");
    }

    private void startLiveness(CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            FaceSDK.Instance().startLiveness(cordova.getContext(), livenessResponse -> {
                try {
                    JSONObject result = new JSONObject();
                    if (livenessResponse.getException() != null) {
                        result.put("error", livenessResponse.getException().getMessage());
                        callbackContext.success(result);
                        return;
                    }

                    int livenessStatus = (livenessResponse.getLiveness() == com.regula.facesdk.enums.LivenessStatus.PASSED) ? 1 : 0;
                    result.put("liveness", livenessStatus);

                    if (livenessResponse.getBitmap() != null) {
                        result.put("image", bitmapToBase64(livenessResponse.getBitmap()));
                    }
                    callbackContext.success(result);
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            });
        });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }
}
