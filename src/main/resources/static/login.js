"use strict";

const csrfCookie = document.cookie
    .split("; ")
    .find(cookie => cookie.startsWith("XSRF-TOKEN="));
if (csrfCookie) {
    const csrfInput = document.createElement("input");
    csrfInput.type = "hidden";
    csrfInput.name = "_csrf";
    csrfInput.value = decodeURIComponent(csrfCookie.split("=")[1]);
    document.querySelector("form").append(csrfInput);
}

if (new URLSearchParams(window.location.search).has("error")) {
    document.querySelector("#login-error").hidden = false;
}
