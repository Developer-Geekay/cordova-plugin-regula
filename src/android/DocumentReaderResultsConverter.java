package com.geekay.plugin.regula;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderTextField;
import com.regula.documentreader.api.results.DocumentReaderTextSource;
import com.regula.documentreader.api.results.DocumentReaderGraphicField;
import com.regula.documentreader.api.results.DocumentReaderGraphicResult;
import com.regula.documentreader.api.results.DocumentReaderTextResult;
import com.regula.documentreader.api.results.DocumentReaderDocumentType;
import com.regula.documentreader.api.results.DocumentReaderBarcodeResult;
import com.regula.documentreader.api.results.DocumentReaderBarcodeField;
import com.regula.documentreader.api.results.DocumentReaderValue;
import com.regula.documentreader.api.results.authenticity.DocumentReaderAuthenticityResult;
import com.regula.documentreader.api.results.authenticity.DocumentReaderAuthenticityCheck;
import com.regula.documentreader.api.results.authenticity.DocumentReaderAuthenticityElement;
import com.regula.documentreader.api.results.rfid.RFIDSessionData;
import com.regula.documentreader.api.results.rfid.AccessControlProcedureType;
import com.regula.documentreader.api.results.rfid.CardProperties;
import com.regula.documentreader.api.results.DocumentReaderResultsStatus;

public class DocumentReaderResultsConverter {

    private static final String TAG = "RegulaPlugin-DocResult";

    /**
     * Converts DocumentReaderResults into a comprehensive JSON object
     * Context is passed to resolve localized field names.
     */
    public static JSONObject toJSON(DocumentReaderResults results, Context context) {
        JSONObject json = new JSONObject();
        if (results == null) return json;

        try {
            // -- Metadata --
            json.put("chipPage", results.chipPage);
            json.put("morePagesAvailable", results.morePagesAvailable);
            json.put("elapsedTime", results.elapsedTime);
            json.put("elapsedTimeRFID", results.elapsedTimeRFID);
            json.put("processingFinishedStatus", results.processingFinishedStatus);

            // -- Result Groups --
            json.put("documentType", convertDocumentType(results));
            json.put("textResult", convertTextResult(results, context));
            json.put("graphicResult", convertGraphicResult(results, context));
            json.put("barcodeResult", convertBarcodeResult(results));
            json.put("authenticityResult", convertAuthenticityResult(results, context));
            json.put("status", convertStatus(results));
            json.put("rfidSessionData", convertRFIDSessionData(results));

        } catch (JSONException e) {
            Log.e(TAG, "Error converting results to JSON", e);
        }

        return json;
    }

    private static JSONArray convertDocumentType(DocumentReaderResults results) throws JSONException {
        JSONArray array = new JSONArray();
        if (results.documentType == null) return array;

        for (DocumentReaderDocumentType docType : results.documentType) {
            JSONObject obj = new JSONObject();
            obj.put("name", docType.name);
            obj.put("documentID", docType.documentID);
            obj.put("ICAOCode", docType.ICAOCode);
            obj.put("dType", docType.dType);
            obj.put("dFormat", docType.dFormat);
            obj.put("dMRZ", docType.dMRZ);
            obj.put("dDescription", docType.dDescription);
            obj.put("dYear", docType.dYear);
            obj.put("dCountryName", docType.dCountryName);
            obj.put("isDeprecated", docType.isDeprecated);
            obj.put("pageIndex", docType.pageIndex);

            if (docType.FDSID != null) {
                JSONArray fdsArray = new JSONArray();
                for (int id : docType.FDSID) {
                    fdsArray.put(id);
                }
                obj.put("FDSID", fdsArray);
            }
            array.put(obj);
        }
        return array;
    }

    private static JSONObject convertTextResult(DocumentReaderResults results, Context context) throws JSONException {
        JSONObject json = new JSONObject();
        DocumentReaderTextResult textResult = results.textResult;
        if (textResult == null) return json;

        json.put("status", textResult.status);
        json.put("comparisonStatus", textResult.comparisonStatus);
        json.put("validityStatus", textResult.validityStatus);

        if (textResult.availableSourceList != null) {
            JSONArray sourceList = new JSONArray();
            for (DocumentReaderTextSource source : textResult.availableSourceList) {
                JSONObject srcObj = new JSONObject();
                srcObj.put("sourceType", source.sourceType);
                srcObj.put("validityStatus", source.validityStatus);
                sourceList.put(srcObj);
            }
            json.put("availableSourceList", sourceList);
        }

        JSONArray fieldsArray = new JSONArray();
        if (textResult.fields != null) {
            for (DocumentReaderTextField field : textResult.fields) {
                JSONObject obj = new JSONObject();
                obj.put("fieldType", field.fieldType);
                obj.put("fieldName", field.getFieldName(context)); // Requires context
                obj.put("lcid", field.lcid);
                obj.put("lcidName", field.getLcidName(context));   // Requires context
                obj.put("value", field.value());
                obj.put("status", field.status);

                if (field.values != null) {
                    JSONArray valuesArray = new JSONArray();
                    for (DocumentReaderValue val : field.values) {
                        JSONObject valObj = new JSONObject();
                        valObj.put("sourceType", val.sourceType);
                        valObj.put("value", val.value);
                        valObj.put("originalValue", val.originalValue);
                        valObj.put("probability", val.probability);
                        valObj.put("pageIndex", val.pageIndex);

                        if (val.boundRect != null) {
                            valObj.put("boundRect", convertRect(val.boundRect));
                        }
                        valuesArray.put(valObj);
                    }
                    obj.put("values", valuesArray);
                }
                fieldsArray.put(obj);
            }
        }
        json.put("fields", fieldsArray);
        return json;
    }

    private static JSONObject convertGraphicResult(DocumentReaderResults results, Context context) throws JSONException {
        JSONObject json = new JSONObject();
        DocumentReaderGraphicResult graphicResult = results.graphicResult;
        if (graphicResult == null) return json;

        JSONArray fieldsArray = new JSONArray();
        if (graphicResult.fields != null) {
            for (DocumentReaderGraphicField field : graphicResult.fields) {
                JSONObject obj = new JSONObject();
                obj.put("sourceType", field.sourceType);
                obj.put("fieldType", field.fieldType);
                obj.put("fieldName", field.getFieldName(context)); // Requires context
                obj.put("pageIndex", field.pageIndex);

                Bitmap bitmap = field.getBitmap();
                if (bitmap != null) {
                    obj.put("imageBase64", bitmapToBase64(bitmap));
                }
                fieldsArray.put(obj);
            }
        }
        json.put("fields", fieldsArray);
        return json;
    }

    private static JSONObject convertBarcodeResult(DocumentReaderResults results) throws JSONException {
        JSONObject json = new JSONObject();
        DocumentReaderBarcodeResult barcodeResult = results.barcodeResult;
        if (barcodeResult == null) return json;

        JSONArray fieldsArray = new JSONArray();
        if (barcodeResult.fields != null) {
            for (DocumentReaderBarcodeField field : barcodeResult.fields) {
                JSONObject obj = new JSONObject();
                obj.put("barcodeType", field.barcodeType);
                obj.put("status", field.status);
                obj.put("pageIndex", field.pageIndex);

                if (field.pdf417Info != null) {
                    JSONObject pdf417 = new JSONObject();
                    pdf417.put("errorLevel", field.pdf417Info.errorLevel);
                    pdf417.put("columns", field.pdf417Info.columns);
                    pdf417.put("rows", field.pdf417Info.rows);
                    obj.put("pdf417Info", pdf417);
                }

                if (field.data != null) {
                    obj.put("data", Base64.encodeToString(field.data, Base64.NO_WRAP));
                }
                fieldsArray.put(obj);
            }
        }
        json.put("fields", fieldsArray);
        return json;
    }

    private static JSONObject convertAuthenticityResult(DocumentReaderResults results, Context context) throws JSONException {
        JSONObject json = new JSONObject();
        DocumentReaderAuthenticityResult authResult = results.authenticityResult;
        if (authResult == null) return json;

        json.put("status", authResult.getStatus());

        JSONArray checksArray = new JSONArray();
        if (authResult.checks != null) {
            for (DocumentReaderAuthenticityCheck check : authResult.checks) {
                JSONObject checkObj = new JSONObject();
                checkObj.put("type", check.type);
                checkObj.put("typeName", check.getTypeName(context)); // Requires context
                checkObj.put("pageIndex", check.pageIndex);
                checkObj.put("status", check.getStatus());            // Uses getter

                JSONArray elementsArray = new JSONArray();
                if (check.elements != null) {
                    for (DocumentReaderAuthenticityElement element : check.elements) {
                        JSONObject elemObj = new JSONObject();
                        elemObj.put("status", element.status);
                        elemObj.put("elementType", element.elementType);
                        elemObj.put("elementTypeName", element.getElementTypeName(context));       // Requires context
                        elemObj.put("elementDiagnose", element.elementDiagnose);
                        elemObj.put("elementDiagnoseName", element.getElementDiagnoseName(context)); // Requires context
                        elementsArray.put(elemObj);
                    }
                }
                checkObj.put("elements", elementsArray);
                checksArray.put(checkObj);
            }
        }
        json.put("checks", checksArray);
        return json;
    }

    private static JSONObject convertStatus(DocumentReaderResults results) throws JSONException {
        JSONObject json = new JSONObject();
        DocumentReaderResultsStatus status = results.status;
        if (status == null) return json;

        // Uses getters to bypass private access compiler errors
        json.put("overallStatus", status.getOverallStatus());
        json.put("optical", status.getOptical());
        json.put("rfid", status.getRfid());
        json.put("portrait", status.getPortrait());
        json.put("stopList", status.getStopList());

        return json;
    }

    private static JSONObject convertRFIDSessionData(DocumentReaderResults results) throws JSONException {
        JSONObject json = new JSONObject();
        RFIDSessionData rfid = results.rfidSessionData;
        if (rfid == null) return json;

        json.put("totalBytesReceived", rfid.totalBytesReceived);
        json.put("totalBytesSent", rfid.totalBytesSent);
        json.put("processTime", rfid.processTime);

        if (rfid.cardProperties != null) {
            JSONObject cardObj = new JSONObject();
            CardProperties cp = rfid.cardProperties;
            cardObj.put("rfidType", cp.rfidType);
            cardObj.put("aTQA", cp.aTQA);
            cardObj.put("aTQB", cp.aTQB);
            cardObj.put("aTR", cp.aTR);
            cardObj.put("uID", cp.uID);
            json.put("cardProperties", cardObj);
        }

        if (rfid.accessControls != null) {
            JSONArray acArray = new JSONArray();
            for (AccessControlProcedureType ac : rfid.accessControls) {
                JSONObject acObj = new JSONObject();
                acObj.put("type", ac.type);
                acObj.put("status", ac.status);
                acArray.put(acObj);
            }
            json.put("accessControls", acArray);
        }

        // Removed deeply nested applications/securityObjects mapping
        // which rely on deprecated objects like `FileData`.
        return json;
    }

    private static String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        byte[] byteArray = stream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    private static JSONObject convertRect(Rect rect) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("left", rect.left);
        obj.put("top", rect.top);
        obj.put("right", rect.right);
        obj.put("bottom", rect.bottom);
        return obj;
    }
}