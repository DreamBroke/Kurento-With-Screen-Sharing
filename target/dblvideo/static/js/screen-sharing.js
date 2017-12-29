/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

var ws = new WebSocket('wss://' + location.host + '/screen-sharing-websocket');
var video;
var webcam;
var screenPeer;
var webcamPeer;

window.onload = function () {
    console = new Console();
    webcam = document.getElementById('webcam');
    video = document.getElementById('video');
    disableStopButton();
};

window.onbeforeunload = function () {
    ws.close();
};

ws.onmessage = function (message) {
    var parsedMessage = JSON.parse(message.data);
    console.info('Received message: ' + message.data);

    switch (parsedMessage.id) {
        case 'screenPresenterResponse':
            presenterResponse(parsedMessage, screenPeer);
            break;
        case 'webcamPresenterResponse':
            presenterResponse(parsedMessage, webcamPeer);
            break;
        case 'screenViewerResponse':
            viewerResponse(parsedMessage, screenPeer);
            break;
        case 'webcamViewerResponse':
            viewerResponse(parsedMessage, webcamPeer);
            break;
        case 'screenIceCandidate':
            screenPeer.addIceCandidate(parsedMessage.candidate, function (error) {
                if (error)
                    return console.error('Error adding candidate: ' + error);
            });
            break;
        case 'webcamIceCandidate':
            webcamPeer.addIceCandidate(parsedMessage.candidate, function (error) {
                if (error)
                    return console.error('Error adding candidate: ' + error);
            });
            break;
        case 'stopCommunication':
            dispose();
            break;
        default:
            console.error('Unrecognized message', parsedMessage);
    }
};

function presenterResponse(message, peer) {
    if (message.response !== 'accepted') {
        var errorMsg = message.message ? message.message : 'Unknow error';
        console.info('Call not accepted for the following reason: ' + errorMsg);
        dispose();
    } else {
        peer.processAnswer(message.sdpAnswer, function (error) {
            if (error)
                return console.error(error);
        });
    }
}

function viewerResponse(message, peer) {
    if (message.response !== 'accepted') {
        var errorMsg = message.message ? message.message : 'Unknow error';
        console.info('Call not accepted for the following reason: ' + errorMsg);
        dispose();
    } else {
        peer.processAnswer(message.sdpAnswer, function (error) {
            if (error)
                return console.error(error);
        });
    }
}

function presenter() {

    if (!screenPeer) {

        showSpinner(video, webcam);

        initiateScreenSharing();

        var options = {
            localVideo: webcam,
            onicecandidate: webcamOnIceCandidate
        };
        webcamPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
            function (error) {
                if (error) {
                    return console.error(error);
                }
                webcamPeer.generateOffer(onOfferWebcamPresenter);
            }
        );

        enableStopButton();
    }
}

function initiateScreenSharing() {
    getScreenId(function (error, sourceId, screen_constraints) {
        console.log("screen_constraints: ");
        if (!screen_constraints) {
            hideSpinner(video, webcam);
            disableStopButton();
            return;
        }
        console.log(screen_constraints);
        navigator.getUserMedia = navigator.mozGetUserMedia || navigator.webkitGetUserMedia;
        navigator.getUserMedia(screen_constraints, function (stream) {
            console.log(stream);

            var constraints = {
                audio: false,
                video: {
                    frameRate: {
                        min: 1, ideal: 15, max: 30
                    },
                    width: {
                        min: 32, ideal: 50, max: 320
                    },
                    height: {
                        min: 32, ideal: 50, max: 320
                    }
                }
            };

            var options = {
                localVideo: video,
                videoStream: stream,
                mediaConstraints: constraints,
                onicecandidate: screenOnIceCandidate,
                sendSource: 'screen'
            };

            screenPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendrecv(options, function (error) {
                if (error) {
                    return console.error(error);
                }
                screenPeer.generateOffer(onOfferScreenPresenter);
            });

        }, function (error) {
            console.error(error);
        });
    });
}

function onOfferWebcamPresenter(error, offerSdp) {
    if (error)
        return console.error('Error generating the offer');
    console.info('Invoking SDP offer callback function ' + location.host);
    var message = {
        id: 'webcam-presenter',
        sendSource: 'screen',
        sdpOffer: offerSdp
    };
    sendMessage(message);
}


function onOfferScreenPresenter(error, offerSdp) {
    if (error)
        return console.error('Error generating the offer');
    console.info('Invoking SDP offer callback function ' + location.host);
    var message = {
        id: 'screen-presenter',
        sendSource: 'screen',
        sdpOffer: offerSdp
    };
    sendMessage(message);
}

function viewer() {

    showSpinner(video, webcam);

    var screen_options = {
        remoteVideo: video,
        onicecandidate: screenOnIceCandidate
    };
    screenPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(screen_options,
        function (error) {
            if (error) {
                return console.error(error);
            }
            this.generateOffer(screenOnOfferViewer);
        });

    var webcam_options = {
        remoteVideo: webcam,
        onicecandidate: webcamOnIceCandidate
    };
    webcamPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(webcam_options,
        function (error) {
            if (error) {
                return console.error(error);
            }
            this.generateOffer(webcamOnOfferViewer);
        });


    enableStopButton();

}

function webcamOnOfferViewer(error, offerSdp, type) {
    if (error)
        return console.error('Error generating the offer');
    console.info('Invoking SDP offer callback function ' + location.host);
    var message = {
        id: "viewer",
        type: "webcam",
        sdpOffer: offerSdp
    };
    sendMessage(message);
}

function screenOnOfferViewer(error, offerSdp, type) {
    if (error)
        return console.error('Error generating the offer');
    console.info('Invoking SDP offer callback function ' + location.host);
    var message = {
        id: "viewer",
        type: "screen",
        sdpOffer: offerSdp
    };
    sendMessage(message);
}

function webcamOnIceCandidate(candidate) {
    console.log("Local candidate" + JSON.stringify(candidate));

    var message = {
        id: 'webcamOnIceCandidate',
        candidate: candidate
    };
    sendMessage(message);
}

function screenOnIceCandidate(candidate) {
    console.log("Local candidate" + JSON.stringify(candidate));

    var message = {
        id: 'screenOnIceCandidate',
        candidate: candidate
    };
    sendMessage(message);
}

function stop() {
    var message = {
        id: 'stop'
    };
    sendMessage(message);
    dispose();
}

function dispose() {
    if (screenPeer) {
        screenPeer.dispose();
        screenPeer = null;
    }
    if (webcamPeer) {
        webcamPeer.dispose();
        webcamPeer = null;
    }
    hideSpinner(video, webcam);

    disableStopButton();
}

function disableStopButton() {
    enableButton('#presenter', 'presenter()');
    enableButton('#viewer', 'viewer()');
    disableButton('#stop');
}

function enableStopButton() {
    disableButton('#presenter');
    disableButton('#viewer');
    enableButton('#stop', 'stop()');
}

function disableButton(id) {
    $(id).attr('disabled', true);
    $(id).removeAttr('onclick');
}

function enableButton(id, functionName) {
    $(id).attr('disabled', false);
    $(id).attr('onclick', functionName);
}

function sendMessage(message) {
    var jsonMessage = JSON.stringify(message);
    console.log('Senging message: ' + jsonMessage);
    ws.send(jsonMessage);
}

function showSpinner() {
    for (var i = 0; i < arguments.length; i++) {
        arguments[i].poster = '../static/img/transparent-1px.png';
        arguments[i].style.background = 'center transparent url("../static/img/spinner.gif") no-repeat';
    }
}

function hideSpinner() {
    for (var i = 0; i < arguments.length; i++) {
        arguments[i].src = '';
        arguments[i].poster = '../static/img/webrtc.png';
        arguments[i].style.background = '';
    }
}

/**
 * Lightbox utility (to display media pipeline image in a modal dialog)
 */
$(document).delegate('*[data-toggle="lightbox"]', 'click', function (event) {
    event.preventDefault();
    $(this).ekkoLightbox();
});