var exec = require('cordova/exec');

var PLUGIN_NAME = 'RegulaFacePlugin';
var PLUGIN_NAME = 'DocumentReaderPlugin';

var Face = {
    initializeFaceSDK: function (licenseBase64, successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'initializeFaceSDK', [licenseBase64]);
    },

    deinitializeFaceSDK: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'deinitializeFaceSDK', []);
    },

    startLiveness: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'startLiveness', []);
    },

    startFaceCapture: function (successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'startFaceCapture', []);
    },

    matchFaces: function (images, successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'matchFaces', [images]);
    },

    detectFace: function (image, successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'detectFace', [image]);
    }
};

var DocumentReader = {

    getAvailableScenarios: function (success, error) {
        exec(success, error, PLUGIN_NAME, 'getAvailableScenarios', []);
    },

    initializeReader: function (config, success, error) {
        // config expected: { license: base64String, licenseUpdateTimeout: 2.0 }
        exec(success, error, PLUGIN_NAME, 'initializeReader', [config]);
    },

    prepareDatabase: function (databaseID, success, error) {
        exec(success, error, PLUGIN_NAME, 'prepareDatabase', [databaseID]);
    },

    runAutoUpdate: function (databaseID, success, error) {
        exec(success, error, PLUGIN_NAME, 'runAutoUpdate', [databaseID]);
    },

    checkDatabaseUpdate: function (databaseID, success, error) {
        exec(success, error, PLUGIN_NAME, 'checkDatabaseUpdate', [databaseID]);
    },

    cancelDBUpdate: function (success, error) {
        exec(success, error, PLUGIN_NAME, 'cancelDBUpdate', []);
    },

    removeDatabase: function (success, error) {
        exec(success, error, PLUGIN_NAME, 'removeDatabase', []);
    },

    startScanner: function (config, success, error) {
        // config expected: { scenario: "ScenarioIdentifier" }
        exec(success, error, PLUGIN_NAME, 'startScanner', [config]);
    },

    recognize: function (config, success, error) {
        // config expected: { scenario: "ScenarioIdentifier", images: [base64...], data: base64String }
        exec(success, error, PLUGIN_NAME, 'recognize', [config]);
    },

    deinitializeReader: function (success, error) {
        exec(success, error, PLUGIN_NAME, 'deinitializeReader', []);
    }
};

Regula = {
    Face: Face,
    DocumentReader: DocumentReader
}

module.exports = Regula;