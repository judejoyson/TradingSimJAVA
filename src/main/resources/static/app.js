const DEFAULT_SYMBOLS = ["AAPL", "MSFT", "NVDA", "TSLA"];
const REFRESH_INTERVAL_MS = 15_000;

let symbols = loadSymbols();
let refreshTimer;
let quoteRefreshVersion = 0;

const elements = {
    quoteGrid: document.querySelector("#quote-grid"),
    positionsBody: document.querySelector("#positions-body"),
    emptyPositions: document.querySelector("#empty-positions"),
    tradeHistory: document.querySelector("#trade-history"),
    emptyTrades: document.querySelector("#empty-trades"),
    cash: document.querySelector("#cash-value"),
    positionCost: document.querySelector("#position-cost"),
    realizedProfitLoss: document.querySelector("#realized-pnl"),
    message: document.querySelector("#message"),
    orderForm: document.querySelector("#order-form"),
    orderButton: document.querySelector("#submit-order")
};

document.querySelector("#symbol-form").addEventListener("submit", addSymbol);
document.querySelector("#reset-account").addEventListener("click", resetAccount);
elements.orderForm.addEventListener("submit", placeOrder);
elements.quoteGrid.addEventListener("click", handleQuoteGridClick);

start();

async function start() {
    renderQuoteLoadingState();
    await Promise.all([refreshQuotes(), refreshAccount()]);
    refreshTimer = window.setInterval(refreshQuotes, REFRESH_INTERVAL_MS);
}

async function api(path, options = {}) {
    const response = await fetch(path, {
        headers: {"Content-Type": "application/json", ...options.headers},
        ...options
    });
    if (!response.ok) {
        let message = `Request failed with status ${response.status}.`;
        try {
            const error = await response.json();
            message = error.message || message;
        } catch {
            // The response was not JSON, so the status-based message is more useful.
        }
        throw new Error(message);
    }
    return response.json();
}

async function refreshQuotes() {
    const requestedSymbols = [...symbols];
    const refreshVersion = ++quoteRefreshVersion;
    const results = await Promise.allSettled(
        requestedSymbols.map(symbol => api(`/api/quotes/${encodeURIComponent(symbol)}`))
    );
    if (refreshVersion !== quoteRefreshVersion) {
        return;
    }
    elements.quoteGrid.replaceChildren();
    results.forEach((result, index) => {
        elements.quoteGrid.append(createQuoteCard(requestedSymbols[index], result));
    });
}

async function refreshAccount() {
    try {
        renderAccount(await api("/api/account"));
    } catch (error) {
        showMessage(error.message, true);
    }
}

async function placeOrder(event) {
    event.preventDefault();
    const form = new FormData(elements.orderForm);
    const order = {
        symbol: String(form.get("symbol")).trim().toUpperCase(),
        side: form.get("side"),
        quantity: Number(form.get("quantity"))
    };

    elements.orderButton.disabled = true;
    elements.orderButton.textContent = "Placing order…";
    try {
        const result = await api("/api/orders", {
            method: "POST",
            body: JSON.stringify(order)
        });
        renderAccount(result.account);
        showMessage(
            `${result.trade.side} order filled: ${result.trade.quantity} `
            + `${result.trade.symbol} at ${formatMoney(result.trade.price)}.`
        );
        if (!symbols.includes(result.trade.symbol)) {
            symbols.push(result.trade.symbol);
            saveSymbols();
        }
        await refreshQuotes();
    } catch (error) {
        showMessage(error.message, true);
    } finally {
        elements.orderButton.disabled = false;
        elements.orderButton.textContent = "Review and place order";
    }
}

async function resetAccount() {
    const confirmed = window.confirm(
        "Reset the simulated account to $100,000 and remove all trades?"
    );
    if (!confirmed) {
        return;
    }
    try {
        renderAccount(await api("/api/account/reset", {method: "POST"}));
        showMessage("The paper-trading account was reset.");
    } catch (error) {
        showMessage(error.message, true);
    }
}

function addSymbol(event) {
    event.preventDefault();
    const input = document.querySelector("#new-symbol");
    const symbol = input.value.trim().toUpperCase();
    if (!/^[A-Z][A-Z0-9.-]{0,9}$/.test(symbol)) {
        showMessage("Enter a valid stock symbol such as AAPL or BRK.B.", true);
        return;
    }
    if (!symbols.includes(symbol)) {
        symbols.push(symbol);
        saveSymbols();
        renderQuoteLoadingState();
        refreshQuotes();
    }
    input.value = "";
}

function handleQuoteGridClick(event) {
    const button = event.target.closest("[data-remove-symbol]");
    if (!button || symbols.length === 1) {
        return;
    }
    symbols = symbols.filter(symbol => symbol !== button.dataset.removeSymbol);
    saveSymbols();
    refreshQuotes();
}

function createQuoteCard(symbol, result) {
    const article = document.createElement("article");
    article.className = "quote-card";

    const removeButton = document.createElement("button");
    removeButton.type = "button";
    removeButton.dataset.removeSymbol = symbol;
    removeButton.setAttribute("aria-label", `Remove ${symbol} from watchlist`);
    removeButton.textContent = "×";
    article.append(removeButton);

    const symbolLabel = document.createElement("strong");
    symbolLabel.className = "quote-symbol";
    symbolLabel.textContent = symbol;
    article.append(symbolLabel);

    if (result.status === "rejected") {
        const error = document.createElement("span");
        error.className = "quote-error";
        error.textContent = result.reason.message;
        article.append(error);
        return article;
    }

    const quote = result.value;
    const price = document.createElement("strong");
    price.className = "quote-price";
    price.textContent = formatMoney(quote.currentPrice);
    article.append(price);

    const change = document.createElement("span");
    const isNegative = Number(quote.change) < 0;
    change.className = `quote-change${isNegative ? " negative" : ""}`;
    change.textContent =
        `${isNegative ? "" : "+"}${formatNumber(quote.change)} `
        + `(${isNegative ? "" : "+"}${formatNumber(quote.percentChange)}%)`;
    article.append(change);
    return article;
}

function renderQuoteLoadingState() {
    elements.quoteGrid.replaceChildren();
    symbols.forEach(symbol => {
        const article = document.createElement("article");
        article.className = "quote-card";
        article.textContent = `Loading ${symbol}…`;
        elements.quoteGrid.append(article);
    });
}

function renderAccount(account) {
    elements.cash.textContent = formatMoney(account.cash);
    const totalCost = account.positions.reduce(
        (sum, position) => sum + Number(position.costBasis),
        0
    );
    elements.positionCost.textContent = formatMoney(totalCost);
    setProfitLoss(elements.realizedProfitLoss, account.realizedProfitLoss);

    elements.positionsBody.replaceChildren();
    account.positions.forEach(position => {
        const row = document.createElement("tr");
        appendCell(row, position.symbol);
        appendCell(row, position.quantity);
        appendCell(row, formatMoney(position.averagePrice));
        appendCell(row, formatMoney(position.costBasis));
        const profitCell = appendCell(
            row,
            formatSignedMoney(position.realizedProfitLoss)
        );
        profitCell.className = Number(position.realizedProfitLoss) < 0 ? "negative" : "positive";
        elements.positionsBody.append(row);
    });
    elements.emptyPositions.hidden = account.positions.length > 0;

    elements.tradeHistory.replaceChildren();
    account.recentTrades.forEach(trade => {
        elements.tradeHistory.append(createTradeRow(trade));
    });
    elements.emptyTrades.hidden = account.recentTrades.length > 0;
}

function createTradeRow(trade) {
    const row = document.createElement("div");
    row.className = "trade-row";

    const icon = document.createElement("span");
    icon.className = `trade-icon${trade.side === "SELL" ? " sell" : ""}`;
    icon.textContent = trade.side === "BUY" ? "B" : "S";

    const details = document.createElement("div");
    const symbol = document.createElement("strong");
    symbol.textContent = trade.symbol;
    const description = document.createElement("small");
    description.textContent =
        `${trade.quantity} shares at ${formatMoney(trade.price)}`;
    details.append(symbol, description);

    const total = document.createElement("span");
    total.className = "trade-total";
    total.textContent =
        `${trade.side === "BUY" ? "−" : "+"}${formatMoney(trade.total)}`;

    row.append(icon, details, total);
    return row;
}

function appendCell(row, value) {
    const cell = document.createElement("td");
    cell.textContent = value;
    row.append(cell);
    return cell;
}

function setProfitLoss(element, value) {
    element.textContent = formatSignedMoney(value);
    element.classList.toggle("negative", Number(value) < 0);
    element.classList.toggle("positive", Number(value) >= 0);
}

function formatMoney(value) {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD"
    }).format(Number(value));
}

function formatSignedMoney(value) {
    const number = Number(value);
    return `${number >= 0 ? "+" : "−"}${formatMoney(Math.abs(number))}`;
}

function formatNumber(value) {
    return Number(value).toFixed(2);
}

function showMessage(text, isError = false) {
    elements.message.textContent = text;
    elements.message.classList.toggle("error", isError);
    elements.message.hidden = false;
    window.setTimeout(() => {
        elements.message.hidden = true;
    }, 6000);
}

function loadSymbols() {
    try {
        const saved = JSON.parse(localStorage.getItem("marketLabSymbols"));
        return Array.isArray(saved) && saved.length > 0 ? saved : [...DEFAULT_SYMBOLS];
    } catch {
        return [...DEFAULT_SYMBOLS];
    }
}

function saveSymbols() {
    localStorage.setItem("marketLabSymbols", JSON.stringify(symbols));
}
