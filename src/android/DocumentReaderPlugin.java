
package com.geekay.plugin.regula;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

// Regula SDK Imports
import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.completions.ICheckDatabaseUpdate;
import com.regula.documentreader.api.completions.IDocumentReaderCompletion;
import com.regula.documentreader.api.completions.IDocumentReaderInitCompletion;
import com.regula.documentreader.api.completions.IDocumentReaderPrepareCompletion;
import com.regula.documentreader.api.config.DocReaderConfig;
import com.regula.documentreader.api.config.RecognizeConfig;
import com.regula.documentreader.api.config.ScannerConfig;
import com.regula.documentreader.api.enums.DocReaderAction;
import com.regula.documentreader.api.results.DocReaderDocumentsDatabase;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderScenario;

public class DocumentReaderPlugin extends CordovaPlugin {

    private static final String TAG = "DocumentReaderPlugin";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        try {
            switch (action) {
                case "getAvailableScenarios":
                    getAvailableScenarios(callbackContext);
                    return true;
                case "initializeReader":
                    initializeReader(args.getJSONObject(0), callbackContext);
                    return true;
                case "prepareDatabase":
                    prepareDatabase(args.getString(0), callbackContext);
                    return true;
                case "runAutoUpdate":
                    runAutoUpdate(args.getString(0), callbackContext);
                    return true;
                case "checkDatabaseUpdate":
                    checkDatabaseUpdate(args.getString(0), callbackContext);
                    return true;
                case "cancelDBUpdate":
                    cancelDBUpdate(callbackContext);
                    return true;
                case "removeDatabase":
                    removeDatabase(callbackContext);
                    return true;
                case "startScanner":
                    startScanner(args.getJSONObject(0), callbackContext);
                    return true;
                case "recognize":
                    recognize(args.getJSONObject(0), callbackContext);
                    return true;
                case "deinitializeReader":
                    deinitializeReader(callbackContext);
                    return true;
            }
        } catch (Exception e) {
            callbackContext.error("Plugin exception: " + e.getMessage());
            return false;
        }
        return false;
    }

    private void getAvailableScenarios(CallbackContext callbackContext) {
        try {
            JSONArray scenariosArray = new JSONArray();
            for (DocumentReaderScenario scenario : DocumentReader.Instance().availableScenarios) {
                scenariosArray.put(scenario.name);
            }
            callbackContext.success(scenariosArray);
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
    }

    private void initializeReader(JSONObject configJson, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                // Read license from JSON base64 string
                String licenseBase64 = configJson.optString("license");
                byte[] license = Base64.decode(licenseBase64, Base64.DEFAULT);

                DocReaderConfig config = new DocReaderConfig(license);
                if (configJson.has("licenseUpdateTimeout")) {
                    config.setLicenseUpdateTimeout(configJson.optDouble("licenseUpdateTimeout", 1.0));
                }

                DocumentReader.Instance().initializeReader(cordova.getActivity(), config, new IDocumentReaderInitCompletion() {
                    @Override
                    public void onInitCompleted(boolean success, Throwable error) {
                        if (success) {
                            callbackContext.success("Init completed successfully");
                        } else {
                            callbackContext.error(error != null ? error.getMessage() : "Init failed");
                        }
                    }
                });
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
        });
    }

    private void prepareDatabase(String databaseID, CallbackContext callbackContext) {
        DocumentReader.Instance().prepareDatabase(cordova.getActivity(), databaseID, new IDocumentReaderPrepareCompletion() {
            @Override
            public void onPrepareProgressChanged(int progress) {
                // Keep callback to send progress updates to JS
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "Progress: " + progress);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
            }

            @Override
            public void onPrepareCompleted(boolean status, Throwable error) {
                if (status) {
                    callbackContext.success("Database prepared");
                } else {
                    callbackContext.error(error != null ? error.getMessage() : "Database prepare failed");
                }
            }
        });
    }

    private void runAutoUpdate(String databaseID, CallbackContext callbackContext) {
        DocumentReader.Instance().runAutoUpdate(cordova.getActivity(), databaseID, new IDocumentReaderPrepareCompletion() {
            @Override
            public void onPrepareProgressChanged(int progress) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "Progress: " + progress);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
            }

            @Override
            public void onPrepareCompleted(boolean status, Throwable error) {
                if (status) {
                    callbackContext.success("Database auto-updated");
                } else {
                    callbackContext.error(error != null ? error.getMessage() : "Database auto-update failed");
                }
            }
        });
    }

    private void checkDatabaseUpdate(String databaseID, CallbackContext callbackContext) {
        DocumentReader.Instance().checkDatabaseUpdate(cordova.getActivity(), databaseID, new ICheckDatabaseUpdate() {
            @Override
            public void onCompleted(DocReaderDocumentsDatabase database) {
                if (database != null) {
                    try {
                        JSONObject dbJson = new JSONObject();
                        dbJson.put("date", database.date);
                        dbJson.put("version", database.version);
                        callbackContext.success(dbJson);
                    } catch (JSONException e) {
                        callbackContext.error("JSON parsing error");
                    }
                } else {
                    callbackContext.error("Database is null");
                }
            }
        });
    }

    private void cancelDBUpdate(CallbackContext callbackContext) {
        DocumentReader.Instance().cancelDBUpdate();
        callbackContext.success("Database update cancelled");
    }

    private void removeDatabase(CallbackContext callbackContext) {
        DocumentReader.Instance().removeDatabase(cordova.getActivity());
        callbackContext.success("Database removed");
    }

    private void startScanner(JSONObject configJson, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                String scenario = configJson.optString("scenario");
                ScannerConfig scannerConfig = new ScannerConfig.Builder(scenario).build();

                DocumentReader.Instance().startScanner(cordova.getActivity(), scannerConfig, getCompletion(callbackContext));
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
        });
    }

    private void recognize(JSONObject configJson, CallbackContext callbackContext) {
        cordova.getThreadPool().execute(() -> {
            try {
                String scenario = configJson.optString("scenario");
                RecognizeConfig.Builder builder = new RecognizeConfig.Builder(scenario);

                if (configJson.has("images")) {
                    JSONArray imagesArray = configJson.getJSONArray("images");
                    Bitmap[] bitmaps = new Bitmap[imagesArray.length()];
                    for (int i = 0; i < imagesArray.length(); i++) {
                        byte[] decodedString = Base64.decode(imagesArray.getString(i), Base64.DEFAULT);
                        bitmaps[i] = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    }
                    builder.setBitmaps(bitmaps);
                } else if (configJson.has("data")) {
                    byte[] data = Base64.decode(configJson.getString("data"), Base64.DEFAULT);
                    builder.setData(data);
                }

                DocumentReader.Instance().recognize(builder.build(), getCompletion(callbackContext));
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
        });
    }

    private void deinitializeReader(CallbackContext callbackContext) {
        DocumentReader.Instance().deinitializeReader();
        callbackContext.success("Deinitialized successfully");
    }

    // Helper completion handler mapped to Cordova callback
    private IDocumentReaderCompletion getCompletion(CallbackContext callbackContext) {
        return new IDocumentReaderCompletion() {
            @Override
            public void onCompleted(int action, DocumentReaderResults results, Throwable error) {
                if (action == DocReaderAction.COMPLETE || action == DocReaderAction.TIMEOUT) {
                    if (results != null) {
                        // Assuming the SDK provides a standard serialization mechanism or you extract needed info
                        // Here returning an empty response as placeholder to avoid massive manual serialization
                        // Reference structure: https://docs.regulaforensics.com/develop/doc-reader-sdk/mobile/getting-started/results/android/
                        callbackContext.success("Processing complete. Implement Results Serialization Here.");
                    } else if (error != null) {
                        callbackContext.error(error.getMessage());
                    }
                }
            }
        };
    }
}