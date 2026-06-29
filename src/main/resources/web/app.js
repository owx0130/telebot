(function () {
    "use strict";

    const tg = window.Telegram ? window.Telegram.WebApp : null;
    if (tg) {
        tg.ready();
        tg.expand();
    }

    const KEY_ROWS = ["qwertyuiop", "asdfghjkl", "↵zxcvbnm⌫"]; // ↵ = Enter, ⌫ = Backspace
    const STATE_CLASS = { 2: "green", 1: "yellow", 0: "absent" };

    const boardEl = document.getElementById("board");
    const keyboardEl = document.getElementById("keyboard");
    const messageEl = document.getElementById("message");
    const modeTagEl = document.getElementById("mode-tag");

    const params = new URLSearchParams(window.location.search);
    const hardModeRequested = params.get("hard") === "true";

    let wordLength = 5;
    let guesses = [];          // [{ word, eval }]
    let keyboardState = {};     // letter -> 0|1|2
    let current = "";           // letters typed for the in-progress row
    let won = false;
    let busy = false;

    const initData = tg ? tg.initData : "";

    function showMessage(text, isWin) {
        messageEl.textContent = text || "";
        messageEl.classList.toggle("win", !!isWin);
    }

    async function api(endpoint, extra) {
        const body = new URLSearchParams();
        body.set("initData", initData || "");
        for (const k in extra) body.set(k, extra[k]);
        const res = await fetch("/api/wordle/" + endpoint, {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: body.toString()
        });
        return res.json();
    }

    function applyState(data) {
        if (typeof data.wordLength === "number") wordLength = data.wordLength;
        if (typeof data.hardMode === "boolean") {
            modeTagEl.textContent = data.hardMode ? "HARD" : "";
        }
        guesses = data.guesses || [];
        keyboardState = data.keyboard || {};
        won = !!data.won;
        current = "";
        render();
        if (won) {
            showMessage(data.answer ? "Solved! 🎉" : "Solved! 🎉", true);
        }
    }

    function render() {
        renderBoard();
        renderKeyboard();
    }

    function renderBoard() {
        boardEl.innerHTML = "";
        for (const g of guesses) {
            boardEl.appendChild(buildRow(g.word, g.eval));
        }
        if (!won) {
            boardEl.appendChild(buildRow(current, null));
        }
    }

    function buildRow(word, evalArr) {
        const row = document.createElement("div");
        row.className = "row";
        for (let i = 0; i < wordLength; i++) {
            const tile = document.createElement("div");
            tile.className = "tile";
            const ch = word[i];
            if (ch) {
                tile.textContent = ch;
                tile.classList.add("filled");
            }
            if (evalArr) tile.classList.add(STATE_CLASS[evalArr[i]]);
            row.appendChild(tile);
        }
        return row;
    }

    function renderKeyboard() {
        keyboardEl.innerHTML = "";
        for (const rowChars of KEY_ROWS) {
            const row = document.createElement("div");
            row.className = "krow";
            for (const ch of rowChars) {
                const key = document.createElement("button");
                key.type = "button";
                if (ch === "↵") {
                    key.textContent = "Enter";
                    key.className = "key wide";
                    key.addEventListener("click", submitGuess);
                } else if (ch === "⌫") {
                    key.textContent = "⌫";
                    key.className = "key wide";
                    key.addEventListener("click", deleteLetter);
                } else {
                    key.textContent = ch;
                    key.className = "key";
                    const state = keyboardState[ch];
                    if (state !== undefined) key.classList.add(STATE_CLASS[state]);
                    key.addEventListener("click", () => addLetter(ch));
                }
                row.appendChild(key);
            }
            keyboardEl.appendChild(row);
        }
    }

    function addLetter(ch) {
        if (won || busy) return;
        if (current.length >= wordLength) return;
        current += ch;
        showMessage("");
        renderBoard();
    }

    function deleteLetter() {
        if (won || busy) return;
        current = current.slice(0, -1);
        renderBoard();
    }

    async function submitGuess() {
        if (won || busy) return;
        if (current.length !== wordLength) {
            showMessage("Not enough letters.");
            return;
        }
        busy = true;
        try {
            const data = await api("guess", { guess: current });
            if (!data.ok) {
                showMessage(data.error || "Invalid guess.");
                return;
            }
            applyState(data);
        } catch (e) {
            showMessage("Network error. Try again.");
        } finally {
            busy = false;
        }
    }

    // Physical keyboard support (desktop Telegram).
    document.addEventListener("keydown", (e) => {
        if (e.key === "Enter") submitGuess();
        else if (e.key === "Backspace") deleteLetter();
        else if (/^[a-zA-Z]$/.test(e.key)) addLetter(e.key.toLowerCase());
    });

    async function init() {
        try {
            const data = await api("start", { hardMode: hardModeRequested });
            if (!data.ok) {
                showMessage(data.error || "Couldn't start the game.");
                render();
                return;
            }
            applyState(data);
        } catch (e) {
            showMessage("Network error. Reopen to try again.");
            render();
        }
    }

    init();
})();
