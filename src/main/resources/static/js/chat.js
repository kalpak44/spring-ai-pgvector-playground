document.addEventListener("DOMContentLoaded", function () {

    // ── Chat ─────────────────────────────────────────────
    const sendButton        = document.getElementById("send-button");
    const chatInput         = document.getElementById("chat-input");
    const messagesContainer = document.getElementById("messages");

    if (!sendButton || !chatInput || !messagesContainer) return;

    chatInput.addEventListener("input", function () {
        this.style.height = "auto";
        this.style.height = Math.min(this.scrollHeight, 144) + "px";
    });

    chatInput.addEventListener("keydown", function (e) {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendButton.click();
        }
    });

    sendButton.addEventListener("click", function () {
        const prompt = chatInput.value.trim();
        if (!prompt) return;
        chatInput.value = "";
        chatInput.style.height = "auto";

        const userDiv = document.createElement("div");
        userDiv.className = "flex items-end justify-end gap-2";
        userDiv.innerHTML = `
            <div class="max-w-xl bg-violet-700 text-white rounded-2xl rounded-br-sm px-4 py-3 text-sm leading-relaxed shadow-md shadow-violet-950/50 bubble">${escapeHtml(prompt)}</div>
            <div class="w-7 h-7 rounded-full bg-zinc-700 border border-zinc-600/60 flex items-center justify-center shrink-0">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 text-zinc-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"/>
                </svg>
            </div>`;
        messagesContainer.appendChild(userDiv);
        scrollToBottom();

        const chatId = window.location.pathname.split("/").pop();
        const url = `/chat-stream/${chatId}?userPrompt=${encodeURIComponent(prompt)}`;

        const aiDiv = document.createElement("div");
        aiDiv.className = "flex items-end gap-2";
        const aiBubble = document.createElement("div");
        aiBubble.className = "max-w-xl bg-zinc-800 text-zinc-100 border border-zinc-700/50 rounded-2xl rounded-bl-sm px-4 py-3 text-sm leading-relaxed shadow-sm bubble";
        aiDiv.innerHTML = `
            <div class="w-7 h-7 rounded-full bg-violet-600 flex items-center justify-center shrink-0 shadow-md shadow-violet-900/40">
                <svg xmlns="http://www.w3.org/2000/svg" class="w-3.5 h-3.5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09z"/>
                </svg>
            </div>`;
        aiDiv.appendChild(aiBubble);
        messagesContainer.appendChild(aiDiv);
        scrollToBottom();

        const eventSource = new EventSource(url);
        let fullText = "";

        eventSource.onmessage = function (event) {
            const data = JSON.parse(event.data);
            fullText += data.text;
            aiBubble.innerHTML = marked.parse(fullText);
            scrollToBottom();
        };

        eventSource.onerror = function () {
            eventSource.close();
        };
    });

    function scrollToBottom() {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    function escapeHtml(text) {
        return text
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;");
    }
});