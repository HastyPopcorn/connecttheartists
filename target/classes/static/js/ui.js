/**
 * ui.js — Reusable UI helpers: autocomplete dropdowns, loading overlay,
 * chain rendering, and other DOM utilities.
 */

const UI = (() => {

    // ─── Loading Overlay ───────────────────────────────────────────────────
    const overlay = document.getElementById('loading-overlay');
    const loadingText = document.getElementById('loading-text');

    function showLoading(msg = 'Searching…') {
        loadingText.textContent = msg;
        overlay.classList.remove('hidden');
    }

    function hideLoading() {
        overlay.classList.add('hidden');
    }

    // ─── Autocomplete ──────────────────────────────────────────────────────

    /**
     * Attach autocomplete behaviour to an input.
     *
     * @param {HTMLInputElement} input
     * @param {HTMLElement} list      - The .autocomplete-list container
     * @param {Function} fetchFn      - async (query) => items[]
     * @param {Function} renderItem   - (item) => { primary, secondary } strings
     * @param {Function} onSelect     - (item) => void
     */
    function attachAutocomplete(input, list, fetchFn, renderItem, onSelect) {
        let debounceTimer;
        let activeIndex = -1;
        let currentItems = [];

        input.addEventListener('input', () => {
            clearTimeout(debounceTimer);
            const q = input.value.trim();
            if (q.length < 2) { closeList(); return; }

            debounceTimer = setTimeout(async () => {
                try {
                    const items = await fetchFn(q);
                    currentItems = items;
                    renderList(items);
                } catch (e) {
                    console.error('Autocomplete fetch error:', e);
                }
            }, 300);
        });

        // Keyboard navigation
        input.addEventListener('keydown', (e) => {
            const items = list.querySelectorAll('.autocomplete-item');
            if (!items.length) return;

            if (e.key === 'ArrowDown') {
                e.preventDefault();
                activeIndex = Math.min(activeIndex + 1, items.length - 1);
                highlight(items);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                activeIndex = Math.max(activeIndex - 1, 0);
                highlight(items);
            } else if (e.key === 'Enter' && activeIndex >= 0) {
                e.preventDefault();
                items[activeIndex].click();
            } else if (e.key === 'Escape') {
                closeList();
            }
        });

        // Close on outside click
        document.addEventListener('click', (e) => {
            if (!input.contains(e.target) && !list.contains(e.target)) closeList();
        });

        function renderList(items) {
            list.innerHTML = '';
            activeIndex = -1;

            if (!items.length) {
                list.innerHTML = '<div class="autocomplete-item"><span class="ac-name" style="color:var(--text-muted)">No results found</span></div>';
                list.classList.add('open');
                return;
            }

            items.forEach((item, i) => {
                const { primary, secondary } = renderItem(item);
                const el = document.createElement('div');
                el.className = 'autocomplete-item';
                el.innerHTML = `
                    <span class="ac-name">${escapeHtml(primary)}</span>
                    ${secondary ? `<span class="ac-meta">${escapeHtml(secondary)}</span>` : ''}
                `;
                el.addEventListener('click', () => {
                    closeList();
                    onSelect(item);
                });
                list.appendChild(el);
            });

            list.classList.add('open');
        }

        function highlight(items) {
            items.forEach((el, i) => el.classList.toggle('active', i === activeIndex));
        }

        function closeList() {
            list.classList.remove('open');
            list.innerHTML = '';
            activeIndex = -1;
        }

        return { closeList };
    }

    // ─── Chain Rendering ───────────────────────────────────────────────────

    /**
     * Render a person node in the chain area.
     * @param {Person} person
     * @param {string} role         - e.g. "vocalist", "producer"
     * @param {boolean} isEndpoint  - true for start/end highlights
     */
    function renderChainPerson(person, role = '', isEndpoint = false) {
        const el = document.createElement('div');
        el.className = 'chain-node';
        el.innerHTML = `
            <div class="chain-person ${isEndpoint ? 'start' : ''}">
                <span class="chain-person-name">${escapeHtml(person.name)}</span>
                ${role ? `<span class="chain-person-role">${escapeHtml(role)}</span>` : ''}
            </div>
        `;
        return el;
    }

    /**
     * Render a song connector row between two people.
     * @param {Song} song
     */
    function renderChainSong(song) {
        const el = document.createElement('div');
        el.className = 'chain-node chain-song-row';
        el.innerHTML = `
            <div class="chain-song-line"></div>
            <div class="chain-song-pill">
                <strong>${escapeHtml(song.title)}</strong>
                ${song.releaseYear ? `<span class="song-year">${escapeHtml(song.releaseYear)}</span>` : ''}
            </div>
        `;
        return el;
    }

    /**
     * Render the full result chain in the result section.
     * @param {ChainResult} result
     * @param {HTMLElement} container
     */
    function renderResultChain(result, container) {
        container.innerHTML = '';

        if (!result.found || !result.steps || result.steps.length === 0) {
            // Direct connection or no steps
            const pill = document.createElement('div');
            pill.className = 'result-person-pill endpoint';
            pill.textContent = result.startPerson.name;
            container.appendChild(pill);

            if (result.steps && result.steps.length === 0) {
                const same = document.createElement('div');
                same.className = 'result-song-connector';
                same.innerHTML = '<span style="font-family:var(--font-mono);font-size:0.75rem;color:var(--accent)">Same person!</span>';
                container.appendChild(same);
            }
            return;
        }

        // Start person
        appendResultPerson(container, result.startPerson, true);

        // Each step: song → person
        result.steps.forEach((step, i) => {
            appendResultSong(container, step.viaSong);
            const nextPerson = result.personChain[i + 1];
            const isEnd = i === result.steps.length - 1;
            if (nextPerson) appendResultPerson(container, nextPerson, isEnd);
        });
    }

    function appendResultPerson(container, person, isEndpoint) {
        const el = document.createElement('div');
        el.className = 'result-person-pill' + (isEndpoint ? ' endpoint' : '');
        el.textContent = person.name;
        container.appendChild(el);
    }

    function appendResultSong(container, song) {
        const el = document.createElement('div');
        el.className = 'result-song-connector';
        el.innerHTML = `
            <div class="line"></div>
            <div class="song-name">♪ ${escapeHtml(song.title)}${song.releaseYear ? ' (' + escapeHtml(song.releaseYear) + ')' : ''}</div>
            <div class="line"></div>
        `;
        container.appendChild(el);
    }

    // ─── Validation message ────────────────────────────────────────────────

    function showValidation(el, message, type) {
        el.textContent = message;
        el.className = 'validation-message ' + type;
        el.classList.remove('hidden');
        setTimeout(() => {
            if (type === 'success') el.classList.add('hidden');
        }, 3000);
    }

    // ─── Misc helpers ──────────────────────────────────────────────────────

    function personRenderItem(person) {
        const meta = [person.type, person.disambiguation].filter(Boolean).join(' · ');
        return { primary: person.name, secondary: meta };
    }

    function songRenderItem(song) {
        const meta = [song.albumName, song.releaseYear].filter(Boolean).join(' · ');
        return { primary: song.title, secondary: meta };
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    // Public API
    return {
        showLoading,
        hideLoading,
        attachAutocomplete,
        renderChainPerson,
        renderChainSong,
        renderResultChain,
        showValidation,
        personRenderItem,
        songRenderItem,
        escapeHtml,
    };
})();
