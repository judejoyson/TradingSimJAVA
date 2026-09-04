"use strict";

const errorBox = document.querySelector("#workspace-error");
let nextDepositAt;
let currentMode;

function csrfHeaders() {
    const cookie = document.cookie
        .split("; ")
        .find(value => value.startsWith("XSRF-TOKEN="));
    return cookie
        ? {"X-XSRF-TOKEN": decodeURIComponent(cookie.split("=")[1])}
        : {};
}

async function jsonRequest(url, options = {}) {
    const response = await fetch(url, options);
    if (response.status === 401) {
        window.location.assign("/login.html");
        throw new Error("Your session has expired.");
    }
    const body = await response.json();
    if (!response.ok) {
        throw new Error(body.message || "The request could not be completed.");
    }
    return body;
}

async function loadWorkspace() {
    try {
        const [user, account, backtests, journal, leaderboard] = await Promise.all([
            jsonRequest("/api/auth/me"),
            jsonRequest("/api/competitive/account"),
            jsonRequest("/api/backtests/history"),
            jsonRequest("/api/journal"),
            jsonRequest(`/api/competitive/leaderboard?sort=${document.querySelector("#leaderboard-sort").value}`)
        ]);
        document.querySelector("#welcome-name").textContent = `Welcome, ${user.displayName}`;
        document.querySelector("#account-email").textContent = user.email;
        renderPortfolio(account);
        renderBacktests(backtests);
        renderJournal(journal);
        renderLeaderboard(leaderboard);
    } catch (error) {
        showError(error.message);
    }
}

function renderPortfolio(account) {
    const competitive = account.mode === "COMPETITIVE";
    currentMode = account.mode;
    document.querySelector("#account-mode").value = account.mode;
    const competitiveLocked = competitive
        && (account.recentTrades.length > 0 || Number(account.totalDeposits) > 0);
    document.querySelector("#account-mode").disabled = competitiveLocked;
    document.querySelector("#mode-form button").disabled = competitiveLocked;
    document.querySelector("#competitive-trade-link").hidden = !competitive;
    document.querySelector("#mode-description").textContent = competitive
        ? competitiveLocked
            ? "Competitive mode is locked after activity begins so leaderboard results cannot be erased."
            : "Competitive mode starts with $250,000 and lets you claim $75,000 every 1 hour 30 minutes."
        : "Normal mode starts with $100,000 and has no scheduled deposits.";
    document.querySelector("#deposit-banner").hidden = !competitive;
    nextDepositAt = competitive ? new Date(account.nextDepositAt).getTime() : undefined;
    if (competitive) {
        document.querySelector("#deposit-amount").textContent = money(account.depositAmount);
    }
    document.querySelector("#claim-deposit-button").disabled =
        !competitive || !account.depositAvailable;
    updateDepositCountdown();
    const metrics = [
        ["Mode", competitive ? "Competitive" : "Normal"],
        ["Starting cash", money(account.startingCash)],
        ["Cash", money(account.cash)],
        ["Portfolio value", money(account.positionsMarketValue)],
        ["Total equity", money(account.totalEquity)],
        ["Unrealized P&L", signedMoney(account.unrealizedProfitLoss)],
        ["Realized P&L", signedMoney(account.realizedProfitLoss)]
    ];
    if (competitive) {
        metrics.push(["Deposits received", money(account.totalDeposits)]);
    }
    document.querySelector("#portfolio-summary").replaceChildren(
        ...metrics.map(([label, value]) => metricCard(label, value))
    );
}

async function updateDepositCountdown() {
    if (!nextDepositAt) {
        return;
    }
    const remaining = nextDepositAt - Date.now();
    if (remaining <= 0) {
        document.querySelector("#deposit-countdown").textContent = "Available now";
        document.querySelector("#claim-deposit-button").disabled = false;
        return;
    }
    const totalSeconds = Math.ceil(remaining / 1000);
    const hours = Math.floor(totalSeconds / 3600);
    const minutes = Math.floor(totalSeconds % 3600 / 60);
    const seconds = totalSeconds % 60;
    document.querySelector("#deposit-countdown").textContent =
        `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function metricCard(label, value) {
    const card = document.createElement("article");
    card.className = "metric-card";
    const caption = document.createElement("span");
    caption.textContent = label;
    const result = document.createElement("strong");
    result.textContent = value;
    card.append(caption, result);
    return card;
}

function renderBacktests(backtests) {
    const container = document.querySelector("#backtest-history");
    if (backtests.length === 0) {
        container.replaceChildren(emptyMessage("No saved backtests yet."));
        return;
    }
    container.replaceChildren(...backtests.map(saved => {
        const item = document.createElement("article");
        item.className = "history-item";
        const title = document.createElement("strong");
        title.textContent = `${saved.symbol} · ${strategyLabel(saved.strategy)}`;
        const detail = document.createElement("p");
        detail.textContent =
            `${dateTime(saved.createdAt)} · ${signedPercent(saved.result.metrics.totalReturnPercent)} return`;
        item.append(title, detail);
        return item;
    }));
}

async function loadLeaderboard() {
    const sort = document.querySelector("#leaderboard-sort").value;
    const entries = await jsonRequest(`/api/competitive/leaderboard?sort=${sort}`);
    renderLeaderboard(entries);
}

function renderLeaderboard(entries) {
    const rows = document.querySelector("#leaderboard-rows");
    if (entries.length === 0) {
        const cell = document.createElement("td");
        cell.className = "empty-table";
        cell.colSpan = 6;
        cell.textContent = "No Competitive players yet.";
        const row = document.createElement("tr");
        row.append(cell);
        rows.replaceChildren(row);
        return;
    }
    rows.replaceChildren(...entries.map(entry => {
        const row = document.createElement("tr");
        [
            entry.rank,
            entry.displayName,
            signedPercent(entry.returnPercent),
            Number(entry.sharpeRatio).toFixed(2),
            `${Number(entry.consistencyPercent).toFixed(2)}%`,
            entry.completedTrades
        ].forEach(value => {
            const cell = document.createElement("td");
            cell.textContent = value;
            row.append(cell);
        });
        return row;
    }));
}

function renderJournal(entries) {
    const container = document.querySelector("#journal-entries");
    if (entries.length === 0) {
        container.replaceChildren(emptyMessage("No journal entries yet."));
        return;
    }
    container.replaceChildren(...entries.map(entry => {
        const item = document.createElement("article");
        item.className = "history-item journal-item";
        const title = document.createElement("strong");
        title.textContent = entry.title;
        const date = document.createElement("small");
        date.textContent = dateTime(entry.createdAt);
        const notes = document.createElement("p");
        notes.textContent = entry.notes;
        item.append(title, date, notes);
        return item;
    }));
}

document.querySelector("#journal-form").addEventListener("submit", async event => {
    event.preventDefault();
    try {
        await jsonRequest("/api/journal", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                ...csrfHeaders()
            },
            body: JSON.stringify({
                title: document.querySelector("#journal-title").value,
                notes: document.querySelector("#journal-notes").value
            })
        });
        event.target.reset();
        renderJournal(await jsonRequest("/api/journal"));
    } catch (error) {
        showError(error.message);
    }
});

document.querySelector("#mode-form").addEventListener("submit", async event => {
    event.preventDefault();
    const mode = document.querySelector("#account-mode").value;
    if (mode === currentMode) {
        return;
    }
    if (!window.confirm("Changing modes resets your portfolio, trades, cash, and deposit timer. Continue?")) {
        await loadWorkspace();
        return;
    }
    try {
        renderPortfolio(await jsonRequest("/api/account/mode", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                ...csrfHeaders()
            },
            body: JSON.stringify({mode})
        }));
        await loadLeaderboard();
    } catch (error) {
        showError(error.message);
    }
});

document.querySelector("#claim-deposit-button").addEventListener("click", async event => {
    event.currentTarget.disabled = true;
    errorBox.hidden = true;
    try {
        renderPortfolio(await jsonRequest("/api/competitive/deposit", {
            method: "POST",
            headers: csrfHeaders()
        }));
        await loadLeaderboard();
    } catch (error) {
        showError(error.message);
        await loadWorkspace();
    }
});

document.querySelector("#leaderboard-sort").addEventListener("change", async () => {
    try {
        await loadLeaderboard();
    } catch (error) {
        showError(error.message);
    }
});

document.querySelector("#logout-button").addEventListener("click", async () => {
    await fetch("/logout", {method: "POST", headers: csrfHeaders()});
    window.location.assign("/");
});

function emptyMessage(message) {
    const paragraph = document.createElement("p");
    paragraph.className = "empty-state";
    paragraph.textContent = message;
    return paragraph;
}

function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
}

function money(value) {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD"
    }).format(Number(value));
}

function signedMoney(value) {
    return `${Number(value) >= 0 ? "+" : ""}${money(value)}`;
}

function signedPercent(value) {
    return `${Number(value) >= 0 ? "+" : ""}${Number(value).toFixed(2)}%`;
}

function strategyLabel(value) {
    return value.toLowerCase().replaceAll("_", " ")
        .replace(/\b\w/g, letter => letter.toUpperCase());
}

function dateTime(value) {
    return new Date(value).toLocaleString();
}

loadWorkspace();
window.setInterval(updateDepositCountdown, 1000);
window.setInterval(() => {
    loadLeaderboard().catch(error => showError(error.message));
}, 30_000);
