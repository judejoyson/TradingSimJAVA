"use strict";

const form = document.querySelector("#register-form");
const error = document.querySelector("#register-error");
const button = document.querySelector("#register-button");
const competitiveSignup = new URLSearchParams(window.location.search).get("competitive") === "true";

if (competitiveSignup) {
    document.querySelector("#competitive-notice").hidden = false;
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    error.hidden = true;
    button.disabled = true;
    try {
        const response = await fetch("/api/auth/register", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                ...csrfHeaders()
            },
            body: JSON.stringify({
                displayName: document.querySelector("#display-name").value,
                email: document.querySelector("#email").value,
                password: document.querySelector("#password").value
            })
        });
        const body = await response.json();
        if (!response.ok) {
            throw new Error(body.message || "The account could not be created.");
        }
        window.location.assign(competitiveSignup ? "/account.html" : "/backtest.html");
    } catch (failure) {
        error.textContent = failure.message;
        error.hidden = false;
        button.disabled = false;
    }
});

function csrfHeaders() {
    const cookie = document.cookie
        .split("; ")
        .find(value => value.startsWith("XSRF-TOKEN="));
    return cookie
        ? {"X-XSRF-TOKEN": decodeURIComponent(cookie.split("=")[1])}
        : {};
}
