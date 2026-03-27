var exec = require('cordova/exec');

var PLUGIN_NAME = 'RegulaForensicsPlugin';

var RegulaFace = {
    initializeFaceSDK: function(licenseBase64, successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'initializeFaceSDK', [licenseBase64]);
    },
    
    deinitializeFaceSDK: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'deinitializeFaceSDK', []);
    },
    
    startLiveness: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'startLiveness', []);
    },
    
    startFaceCapture: function(successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'startFaceCapture', []);
    },
    
    matchFaces: function(images, successCallback, errorCallback) {
        exec(successCallback, errorCallback, PLUGIN_NAME, 'matchFaces', [images]);
    }
};

module.exports = RegulaFace;
