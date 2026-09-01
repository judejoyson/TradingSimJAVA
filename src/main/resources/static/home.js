const form = document.querySelector("#simulation-form");
const marketContainer = document.querySelector("#market-options");
const timeframeContainer = document.querySelector("#timeframe-options");
const instrumentSelect = document.querySelector("#instrument");
const startButton = document.querySelector("#start-button");
const errorBox = document.querySelector("#home-error");

let options;
let selectedMarket;

loadOptions();

async function loadOptions() {
    try {
        const response = await fetch("/api/replay/options");
        if (!response.ok) {
            throw new Error("The simulator options could not be loaded.");
        }
        options = await response.json();
        renderMarkets();
        renderTimeframes();
        selectMarket(options.markets[0].id);
        startButton.disabled = false;
    } catch (error) {
        showError(error.message);
    }
}

function renderMarkets() {
    marketContainer.replaceChildren();
    options.markets.forEach(market => {
        const label = document.createElement("label");
        label.className = "market-choice";

        const input = document.createElement("input");
        input.type = "radio";
        input.name = "market";
        input.value = market.id;
        input.checked = market === options.markets[0];
        input.addEventListener("change", () => selectMarket(market.id));

        const body = document.createElement("span");
        body.className = "market-choice-body";

        const icon = document.createElement("span");
        icon.className = "market-choice-icon";
        icon.textContent = marketIcon(market.id);

        const text = document.createElement("span");
        const title = document.createElement("strong");
        title.textContent = market.label;
        const description = document.createElement("small");
        description.textContent = market.description;
        text.append(title, description);

        body.append(icon, text);
        label.append(input, body);
        marketContainer.append(label);
    });
}

function renderTimeframes() {
    timeframeContainer.replaceChildren();
    options.timeframes.forEach((timeframe, index) => {
        const label = document.createElement("label");
        label.className = "timeframe-choice";

        const input = document.createElement("input");
        input.type = "radio";
        input.name = "timeframe";
        input.value = timeframe.id;
        input.checked = index === 1;

        const display = document.createElement("span");
        display.textContent = timeframe.id;
        display.title = timeframe.label;

        label.append(input, display);
        timeframeContainer.append(label);
    });
}

function selectMarket(marketId) {
    selectedMarket = options.markets.find(market => market.id === marketId);
    instrumentSelect.replaceChildren();
    selectedMarket.instruments.forEach(instrument => {
        const option = document.createElement("option");
        option.value = instrument.symbol;
        option.textContent = `${instrument.symbol} — ${instrument.name}`;
        instrumentSelect.append(option);
    });
    instrumentSelect.disabled = false;
}

form.addEventListener("submit", event => {
    event.preventDefault();
    if (!selectedMarket) {
        showError("Choose a market before starting.");
        return;
    }

    const data = new FormData(form);
    const query = new URLSearchParams({
        market: data.get("market"),
        symbol: data.get("symbol"),
        timeframe: data.get("timeframe")
    });
    window.location.href = `/chart.html?${query}`;
});

function marketIcon(marketId) {
    return {
        STOCKS: "▥",
        FOREX: "⇄",
        CRYPTO: "◇"
    }[marketId] || "●";
}

function showError(message) {
    errorBox.textContent = message;
    errorBox.hidden = false;
}
