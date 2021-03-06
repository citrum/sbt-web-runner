goog.provide('web_runner_client');

goog.require('goog.net.cookies');

var domain = 'example.com';
var evtSource = new EventSource("http://127.0.0.1:9002/");

var autoReloadCookie = '_autoreload';
var cookieMaxAgeSeconds = 365 * 3600 * 24;

var autoReload = goog.net.cookies.get(autoReloadCookie, 0);
var state = "...";

var $baloon = $('<div id="web-runner-baloon">...</div>').appendTo('body');

evtSource.onmessage = function(e) {
  var newState = JSON.parse(e.data)['state'];
  if (autoReload) {
    var afterCompile = (state == 'compiling' || state == 'compile-error') && newState == 'running';
    var assetChanged = newState == 'asset-changed';
    if (afterCompile || assetChanged) {
      window.location.reload();
    }
  }
  state = newState;
  updateBaloon();
};
evtSource.onerror = function(e) {
  state = 'sbt-is-down';
  updateBaloon();
};

function updateBaloon() {
  $baloon.html(state + " <span class='ar" + (autoReload ? 1 : 0) + "'>↻</span>").attr('class', state);
}

function toggleAutoReload() {
  autoReload ^= 1;
  goog.net.cookies.set(autoReloadCookie, autoReload ? 1 : 0, cookieMaxAgeSeconds, '/', '.' + domain);
  updateBaloon();
}

$baloon.click(toggleAutoReload);
