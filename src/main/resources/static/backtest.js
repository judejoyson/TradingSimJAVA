"use strict";

const elements = {
    form: document.querySelector("#backtest-form"),
    dataSource: document.querySelector("#data-source"),
    market: document.querySelector("#backtest-market"),
    symbol: document.querySelector("#backtest-symbol"),
    generatedSymbolField: document.querySelector("#generated-symbol-field"),
    customSymbolField: document.querySelector("#custom-symbol-field"),
    customSymbol: document.querySelector("#custom-symbol"),
    csvPanel: document.querySelector("#csv-upload-panel"),
    csvFile: document.querySelector("#csv-file"),
    strategy: document.querySelector("#backtest-strategy"),
    description: document.querySelector("#strategy-description"),
    movingAverage: document.querySelector("#moving-average-settings"),
    rsi: document.querySelector("#rsi-settings"),
    error: document.querySelector("#backtest-error"),
    runButton: document.querySelector("#run-backtest"),
    results: document.querySelector("#backtest-results"),
    metrics: document.querySelector("#metric-cards"),
    executionRows: document.querySelector("#execution-rows"),
    canvas: document.querySelector("#equity-chart")
};

let options;
let latestResult;

async function loadOptions() {
    try {
        const response = await fetch("/api/backtests/options");
        if (!response.ok) {
            throw new Error("Backtest choices could not be loaded.");
        }
        options = await response.json();
        fillSelect(elements.market, options.markets, "id", "label");
        fillSelect(elements.strategy, options.strategies, "id", "name");
        document.querySelector("#start-date").min = options.earliestDate;
        document.querySelector("#end-date").max = options.latestDate;
        updateInstruments();
        updateStrategySettings();
        updateDataSource();
        elements.market.disabled = false;
        elements.symbol.disabled = false;
        elements.strategy.disabled = false;
        elements.runButton.disabled = false;
    } catch (error) {
        showError(error.message);
    }
}

function fillSelect(select, choices, valueProperty, labelProperty) {
    select.replaceChildren(...choices.map(choice => {
        const option = document.createElement("option");
        option.value = choice[valueProperty];
        option.textContent = choice[labelProperty];
        return option;
    }));
}

function updateInstruments() {
    const market = options.markets.find(choice => choice.id === elements.market.value);
    fillSelect(elements.symbol, market.instruments, "symbol", "name");
}

function updateStrategySettings() {
    const strategy = options.strategies.find(choice => choice.id === elements.strategy.value);
    elements.description.textContent = strategy.description;
    elements.movingAverage.hidden = strategy.id !== "MOVING_AVERAGE";
    elements.rsi.hidden = strategy.id !== "RSI";
}

function updateDataSource() {
    const uploadingCsv = elements.dataSource.value === "CSV";
    elements.generatedSymbolField.hidden = uploadingCsv;
    elements.customSymbolField.hidden = !uploadingCsv;
    elements.csvPanel.hidden = !uploadingCsv;
    elements.symbol.required = !uploadingCsv;
    elements.customSymbol.required = uploadingCsv;
    elements.csvFile.required = uploadingCsv;

    const startDate = document.querySelector("#start-date");
    const endDate = document.querySelector("#end-date");
    startDate.min = uploadingCsv ? "" : options.earliestDate;
    endDate.max = uploadingCsv ? "" : options.latestDate;
}

async function updateDatesFromCsv() {
    const file = elements.csvFile.files[0];
    if (!file) {
        return;
    }
    const lines = (await file.text()).split(/\r?\n/).filter(line => line.trim());
    if (lines.length < 3) {
        return;
    }
    const headers = lines[0].split(",").map(header =>
        header.replace(/["_\s]/g, "").toLowerCase());
    const dateIndex = headers.indexOf("date");
    if (dateIndex < 0) {
        return;
    }
    const dates = lines.slice(1)
        .map(line => normalizeCsvDate(line.split(",")[dateIndex]))
        .filter(Boolean)
        .sort();
    if (dates.length > 0) {
        document.querySelector("#start-date").value = dates[0];
        document.querySelector("#end-date").value = dates[dates.length - 1];
    }
}

function normalizeCsvDate(rawDate) {
    const value = rawDate?.replaceAll('"', "").trim();
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        return value;
    }
    const match = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(value);
    return match
        ? `${match[3]}-${match[1].padStart(2, "0")}-${match[2].padStart(2, "0")}`
        : null;
}

async function runBacktest(event) {
    event.preventDefault();
    hideError();
    elements.runButton.disabled = true;
    elements.runButton.textContent = "Running…";

    const payload = {
        market: elements.market.value,
        symbol: elements.dataSource.value === "CSV"
            ? elements.customSymbol.value.trim()
            : elements.symbol.value,
        strategy: elements.strategy.value,
        startDate: document.querySelector("#start-date").value,
        endDate: document.querySelector("#end-date").value,
        startingCash: Number(document.querySelector("#starting-cash").value),
        feeRateBps: Number(document.querySelector("#fee-rate").value),
        fastPeriod: Number(document.querySelector("#fast-period").value),
        slowPeriod: Number(document.querySelector("#slow-period").value),
        rsiPeriod: Number(document.querySelector("#rsi-period").value),
        oversold: Number(document.querySelector("#oversold").value),
        overbought: Number(document.querySelector("#overbought").value)
    };

    try {
        const response = await sendBacktest(payload);
        const body = await response.json();
        if (!response.ok) {
            throw new Error(body.message || "The backtest could not be completed.");
        }
        latestResult = body;
        renderResult(body);
    } catch (error) {
        showError(error.message);
    } finally {
        elements.runButton.disabled = false;
        elements.runButton.textContent = "Run historical backtest";
    }
}

function sendBacktest(payload) {
    if (elements.dataSource.value === "CSV") {
        const formData = new FormData();
        formData.append(
            "request",
            new Blob([JSON.stringify(payload)], {type: "application/json"}));
        formData.append("file", elements.csvFile.files[0]);
        return fetch("/api/backtests/csv", {
            method: "POST",
            body: formData
        });
    }
    return fetch("/api/backtests", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
    });
}

function renderResult(result) {
    document.querySelector("#result-title").textContent =
        `${result.symbol} · ${strategyName(result.strategy)}`;
    document.querySelector("#result-period").textContent =
        `${result.startDate} to ${result.endDate} · ${result.candleCount} candles · ${result.instrumentName}`;

    const metrics = result.metrics;
    const cards = [
        ["Ending equity", currency(metrics.endingEquity)],
        ["Net profit", signedCurrency(metrics.netProfit), tone(metrics.netProfit)],
        ["Total return", signedPercent(metrics.totalReturnPercent), tone(metrics.totalReturnPercent)],
        ["Buy & hold", signedPercent(metrics.benchmarkReturnPercent), tone(metrics.benchmarkReturnPercent)],
        ["Max drawdown", `-${number(metrics.maxDrawdownPercent)}%`, "negative"],
        ["Annualized return", signedPercent(metrics.annualizedReturnPercent), tone(metrics.annualizedReturnPercent)],
        ["Win rate", `${number(metrics.winRatePercent)}%`, ""],
        ["Completed trades", String(metrics.completedTrades), ""],
        ["Fees paid", currency(metrics.totalFees), ""]
    ];
    elements.metrics.replaceChildren(...cards.map(([label, value, className]) => {
        const card = document.createElement("article");
        card.className = "metric-card";
        const title = document.createElement("span");
        title.textContent = label;
        const metric = document.createElement("strong");
        metric.textContent = value;
        if (className) {
            metric.classList.add(className);
        }
        card.append(title, metric);
        return card;
    }));

    renderExecutions(result.executions);
    elements.results.hidden = false;
    requestAnimationFrame(() => {
        drawEquityChart(result.equityCurve);
        elements.results.scrollIntoView({behavior: "smooth", block: "start"});
    });
}

function renderExecutions(executions) {
    if (executions.length === 0) {
        const row = document.createElement("tr");
        const cell = document.createElement("td");
        cell.colSpan = 6;
        cell.className = "empty-table";
        cell.textContent = "This strategy did not generate an execution in the selected period.";
        row.append(cell);
        elements.executionRows.replaceChildren(row);
        return;
    }
    elements.executionRows.replaceChildren(...executions.map(execution => {
        const row = document.createElement("tr");
        [
            execution.date,
            execution.side,
            currency(execution.price),
            number(execution.quantity, 6),
            currency(execution.fee),
            execution.reason
        ].forEach((value, index) => {
            const cell = document.createElement("td");
            cell.textContent = value;
            if (index === 1) {
                cell.className = execution.side === "BUY" ? "positive" : "negative";
            }
            row.append(cell);
        });
        return row;
    }));
}

// The chart uses the browser Canvas API, keeping this page dependency-free.
function drawEquityChart(points) {
    const canvas = elements.canvas;
    const ratio = window.devicePixelRatio || 1;
    const width = canvas.clientWidth;
    const height = 340;
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    const context = canvas.getContext("2d");
    context.scale(ratio, ratio);
    context.clearRect(0, 0, width, height);

    const padding = {top: 20, right: 18, bottom: 32, left: 72};
    const values = points.flatMap(point => [Number(point.equity), Number(point.benchmarkEquity)]);
    const minimum = Math.min(...values);
    const maximum = Math.max(...values);
    const spread = maximum - minimum || 1;
    const plotWidth = width - padding.left - padding.right;
    const plotHeight = height - padding.top - padding.bottom;
    const x = index => padding.left + index / Math.max(1, points.length - 1) * plotWidth;
    const y = value => padding.top + (maximum - value) / spread * plotHeight;

    context.strokeStyle = "#dcded8";
    context.fillStyle = "#68737d";
    context.font = "12px system-ui";
    for (let line = 0; line <= 4; line++) {
        const value = maximum - spread * line / 4;
        const lineY = padding.top + plotHeight * line / 4;
        context.beginPath();
        context.moveTo(padding.left, lineY);
        context.lineTo(width - padding.right, lineY);
        context.stroke();
        context.fillText(currency(value), 5, lineY + 4);
    }

    drawLine(context, points, point => point.benchmarkEquity, x, y, "#c8943e");
    drawLine(context, points, point => point.equity, x, y, "#1d7a55");
    context.fillText(points[0].date, padding.left, height - 8);
    const finalDate = points[points.length - 1].date;
    const dateWidth = context.measureText(finalDate).width;
    context.fillText(finalDate, width - padding.right - dateWidth, height - 8);
}

function drawLine(context, points, value, x, y, color) {
    context.beginPath();
    points.forEach((point, index) => {
        const draw = index === 0 ? context.moveTo.bind(context) : context.lineTo.bind(context);
        draw(x(index), y(Number(value(point))));
    });
    context.strokeStyle = color;
    context.lineWidth = 2;
    context.stroke();
}

function strategyName(id) {
    return options.strategies.find(strategy => strategy.id === id)?.name || id;
}

function currency(value) {
    return new Intl.NumberFormat("en-US", {
        style: "currency",
        currency: "USD",
        maximumFractionDigits: 2
    }).format(Number(value));
}

function number(value, digits = 2) {
    return new Intl.NumberFormat("en-US", {maximumFractionDigits: digits}).format(Number(value));
}

function signedCurrency(value) {
    return `${Number(value) >= 0 ? "+" : ""}${currency(value)}`;
}

function signedPercent(value) {
    return `${Number(value) >= 0 ? "+" : ""}${number(value)}%`;
}

function tone(value) {
    return Number(value) >= 0 ? "positive" : "negative";
}

function showError(message) {
    elements.error.textContent = message;
    elements.error.hidden = false;
}

function hideError() {
    elements.error.hidden = true;
}

elements.market.addEventListener("change", updateInstruments);
elements.strategy.addEventListener("change", updateStrategySettings);
elements.dataSource.addEventListener("change", updateDataSource);
elements.csvFile.addEventListener("change", updateDatesFromCsv);
elements.form.addEventListener("submit", runBacktest);
window.addEventListener("resize", () => {
    if (latestResult) {
        drawEquityChart(latestResult.equityCurve);
    }
});

loadOptions();
