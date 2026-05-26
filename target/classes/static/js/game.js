/**
 * game.js — Music Chain graph-based gameplay.
 *
 * Players build a graph of people and songs. The game is won when
 * a path exists between the start and end person through the graph.
 *
 * Nodes = People or Songs (click to select)
 * Edges = Person contributed to Song
 *
 * Adding rules:
 *   - Any song can be added if any contributor is already on the board
 *   - Any person can be added if any of their songs is already on the board
 */
(function () {
    'use strict';

    // ─── Graph ────────────────────────────────────────────────────────────────

    const graph = {
        nodes: new Map(), // id → { id, type:'person'|'song', data, x, y }
        edges: new Set(), // "personId|songId"

        add(type, data, x, y) {
            if (this.nodes.has(data.id)) return false;
            this.nodes.set(data.id, { id: data.id, type, data, x, y });
            return true;
        },

        link(personId, songId) {
            this.edges.add(`${personId}|${songId}`);
        },

        neighbors(id) {
            const result = [];
            for (const edge of this.edges) {
                const [p, s] = edge.split('|');
                if (p === id) result.push(s);
                else if (s === id) result.push(p);
            }
            return result;
        },

        personIds() {
            return [...this.nodes.values()].filter(n => n.type === 'person').map(n => n.id);
        },

        songIds() {
            return [...this.nodes.values()].filter(n => n.type === 'song').map(n => n.id);
        },

        // BFS — returns node id path if connected, null otherwise
        findPath(startId, endId) {
            if (!this.nodes.has(startId) || !this.nodes.has(endId)) return null;
            const prev = new Map();
            const visited = new Set([startId]);
            const queue = [startId];
            while (queue.length) {
                const cur = queue.shift();
                if (cur === endId) {
                    const path = [];
                    let node = endId;
                    while (node !== undefined) { path.unshift(node); node = prev.get(node); }
                    return path;
                }
                for (const nb of this.neighbors(cur)) {
                    if (!visited.has(nb)) {
                        visited.add(nb);
                        prev.set(nb, cur);
                        queue.push(nb);
                    }
                }
            }
            return null;
        },

        clear() {
            this.nodes.clear();
            this.edges.clear();
        },
    };

    // ─── State ────────────────────────────────────────────────────────────────

    const state = {
        phase: 'setup',
        startPerson: null,
        endPerson: null,
        selectedNodeId: null,
        nodeElements: new Map(),  // id → DOM element
        edgeElements: new Map(),  // "personId|songId" → SVG element
        complete: false,
    };

    // ─── DOM ──────────────────────────────────────────────────────────────────

    const $ = id => document.getElementById(id);
    const dom = {
        navBtns: document.querySelectorAll('.nav-btn'),
        howPanel: $('how-panel'),
        setupSection: $('setup-section'),
        startInput: $('start-input'),
        startAutocomplete: $('start-autocomplete'),
        startSelected: $('start-selected'),
        startName: $('start-name'),
        startMeta: $('start-meta'),
        endInput: $('end-input'),
        endAutocomplete: $('end-autocomplete'),
        endSelected: $('end-selected'),
        endName: $('end-name'),
        endMeta: $('end-meta'),
        startGameBtn: $('start-game-btn'),
        gameSection: $('game-section'),
        gameStartName: $('game-start-name'),
        gameEndName: $('game-end-name'),
        nodeCount: $('node-count'),
        graphContainer: $('graph-container'),
        graphSvg: $('graph-svg'),
        graphNodes: $('graph-nodes'),
        actionInstruction: $('action-instruction'),
        actionSearch: $('action-search'),
        nodeSearchInput: $('node-search-input'),
        nodeAutocomplete: $('node-autocomplete'),
        hintBtn: $('hint-btn'),
        giveUpBtn: $('give-up-btn'),
        restartBtn: $('restart-btn'),
        resultSection: $('result-section'),
        resultEmoji: $('result-emoji'),
        resultTitle: $('result-title'),
        resultSubtitle: $('result-subtitle'),
        resultChain: $('result-chain'),
        playAgainBtn: $('play-again-btn'),
    };

    // ─── Navigation ───────────────────────────────────────────────────────────

    dom.navBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            const mode = btn.dataset.mode;
            dom.navBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            if (mode === 'how') dom.howPanel.classList.toggle('hidden');
            else dom.howPanel.classList.add('hidden');
        });
    });

    // ─── Setup ────────────────────────────────────────────────────────────────

    UI.attachAutocomplete(
        dom.startInput, dom.startAutocomplete,
        q => API.searchPeople(q), UI.personRenderItem,
        p => selectSetupPerson('start', p)
    );

    UI.attachAutocomplete(
        dom.endInput, dom.endAutocomplete,
        q => API.searchPeople(q), UI.personRenderItem,
        p => selectSetupPerson('end', p)
    );

    function selectSetupPerson(which, person) {
        state[which === 'start' ? 'startPerson' : 'endPerson'] = person;
        const nameEl = which === 'start' ? dom.startName : dom.endName;
        const metaEl = which === 'start' ? dom.startMeta : dom.endMeta;
        const selEl  = which === 'start' ? dom.startSelected : dom.endSelected;
        const inpEl  = which === 'start' ? dom.startInput : dom.endInput;
        nameEl.textContent = person.name;
        metaEl.textContent = [person.type, person.disambiguation].filter(Boolean).join(' · ');
        selEl.classList.remove('hidden');
        inpEl.style.display = 'none';
        updateStartBtn();
    }

    function clearSetupPerson(which) {
        state[which === 'start' ? 'startPerson' : 'endPerson'] = null;
        const selEl = which === 'start' ? dom.startSelected : dom.endSelected;
        const inpEl = which === 'start' ? dom.startInput : dom.endInput;
        selEl.classList.add('hidden');
        inpEl.style.display = '';
        inpEl.value = '';
        updateStartBtn();
    }

    document.querySelectorAll('.clear-btn[data-target]').forEach(btn => {
        btn.addEventListener('click', () => clearSetupPerson(btn.dataset.target));
    });

    function updateStartBtn() {
        dom.startGameBtn.disabled = !(state.startPerson && state.endPerson);
    }

    dom.startGameBtn.addEventListener('click', startGame);

    // ─── Game Start ───────────────────────────────────────────────────────────

    const GRAPH_W = 1200;
    const GRAPH_H = 560;
    const NODE_MIN_GAP = 160;

    function startGame() {
        // Reset everything
        graph.clear();
        state.nodeElements.clear();
        state.edgeElements.clear();
        state.selectedNodeId = null;
        state.complete = false;

        dom.graphNodes.innerHTML = '';
        dom.graphSvg.innerHTML = '';
        dom.graphSvg.setAttribute('width', GRAPH_W);
        dom.graphSvg.setAttribute('height', GRAPH_H);
        dom.graphSvg.style.width = GRAPH_W + 'px';
        dom.graphSvg.style.height = GRAPH_H + 'px';
        dom.graphNodes.style.width = GRAPH_W + 'px';
        dom.graphNodes.style.height = GRAPH_H + 'px';

        dom.gameStartName.textContent = state.startPerson.name;
        dom.gameEndName.textContent = state.endPerson.name;
        dom.hintBtn.disabled = true;

        dom.setupSection.classList.add('hidden');
        dom.resultSection.classList.add('hidden');
        dom.gameSection.classList.remove('hidden');

        // Place start and end people at opposite sides, vertically centred
        addNodeToGraph('person', state.startPerson, 100, GRAPH_H / 2, true);
        addNodeToGraph('person', state.endPerson,   GRAPH_W - 100, GRAPH_H / 2, true);

        updateNodeCount();
        updateActionPanel();
    }

    // ─── Node Management ─────────────────────────────────────────────────────

    function addNodeToGraph(type, data, x, y, instant = false) {
        if (!graph.add(type, data, x, y)) return; // already exists

        const el = createNodeEl(type, data);
        positionEl(el, x, y, type);

        if (!instant) {
            el.style.opacity = '0';
            el.style.transform = 'scale(0.5)';
        }

        makeDraggable(el, data.id);
        dom.graphNodes.appendChild(el);
        state.nodeElements.set(data.id, el);

        if (!instant) {
            requestAnimationFrame(() => {
                el.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
                el.style.opacity = '1';
                el.style.transform = 'scale(1)';
            });
        }
    }

    function createNodeEl(type, data) {
    const el = document.createElement('div');
    el.className = `graph-node graph-node--${type}`;
    el.dataset.id = data.id;

    const isEndpoint = data.id === state.startPerson?.id || data.id === state.endPerson?.id;
    if (isEndpoint) el.classList.add('graph-node--endpoint');

    if (type === 'person') {
        el.innerHTML = `
            <span class="gn-type">Person</span>
            <span class="gn-name">${UI.escapeHtml(data.name)}</span>
        `;
    } else {
        el.innerHTML = `
            <span class="gn-type">Song${data.releaseYear ? ' · ' + UI.escapeHtml(data.releaseYear) : ''}</span>
            <span class="gn-name">${UI.escapeHtml(data.title)}</span>
        `;
    }
    return el;
}

    function positionEl(el, x, y, type) {
        const w = 144;
        const h = type === 'person' ? 50 : 42;
        el.style.position = 'absolute';
        el.style.left = `${x - w / 2}px`;
        el.style.top  = `${y - h / 2}px`;
    }

    function addEdgeToGraph(personId, songId) {
        const key = `${personId}|${songId}`;
        if (state.edgeElements.has(key)) return;
        graph.link(personId, songId);

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('class', 'graph-edge');
        refreshLine(line, personId, songId);
        dom.graphSvg.appendChild(line);
        state.edgeElements.set(key, line);
    }

    function refreshLine(line, personId, songId) {
        const p = graph.nodes.get(personId);
        const s = graph.nodes.get(songId);
        if (!p || !s) return;
        line.setAttribute('x1', p.x); line.setAttribute('y1', p.y);
        line.setAttribute('x2', s.x); line.setAttribute('y2', s.y);
    }

    // Place a new node near a connected one, avoiding overlaps
    function findPosition(nearId) {
        const near = graph.nodes.get(nearId);
        for (let attempt = 0; attempt < 24; attempt++) {
            const angle = (attempt / 12) * Math.PI + Math.random() * 0.4;
            const dist  = NODE_MIN_GAP + Math.random() * 60;
            let x = near.x + Math.cos(angle) * dist;
            let y = near.y + Math.sin(angle) * dist;
            x = Math.max(80, Math.min(GRAPH_W - 80, x));
            y = Math.max(40, Math.min(GRAPH_H - 40, y));

            const tooClose = [...graph.nodes.values()].some(n => {
                if (n.id === nearId) return false;
                const dx = x - n.x, dy = y - n.y;
                return Math.sqrt(dx * dx + dy * dy) < NODE_MIN_GAP;
            });
            if (!tooClose) return { x, y };
        }
        // Fallback
        return {
            x: Math.max(80, Math.min(GRAPH_W - 80, near.x + 170)),
            y: Math.max(40, Math.min(GRAPH_H - 40, near.y + (Math.random() - 0.5) * 120)),
        };
    }

    // ─── Node Selection ───────────────────────────────────────────────────────

    function selectNode(id) {
        if (state.complete) return;

        // Deselect previous
        if (state.selectedNodeId) {
            state.nodeElements.get(state.selectedNodeId)?.classList.remove('graph-node--selected');
        }

        // Toggle off if clicking same node
        if (state.selectedNodeId === id) {
            state.selectedNodeId = null;
            dom.hintBtn.disabled = true;
            updateActionPanel();
            return;
        }

        state.selectedNodeId = id;
        state.nodeElements.get(id)?.classList.add('graph-node--selected');
        dom.hintBtn.disabled = false;
        updateActionPanel();
    }

    function updateActionPanel() {
        dom.nodeSearchInput.value = '';

        if (!state.selectedNodeId) {
            dom.actionInstruction.textContent = 'Click a node to add connections';
            dom.actionSearch.classList.add('hidden');
            return;
        }

        const node = graph.nodes.get(state.selectedNodeId);
        if (!node) return;

        if (node.type === 'person') {
            dom.actionInstruction.innerHTML =
                `Add a song that <strong>${UI.escapeHtml(node.data.name)}</strong> contributed to:`;
        } else {
            dom.actionInstruction.innerHTML =
                `Add a person who contributed to <strong>${UI.escapeHtml(node.data.title)}</strong>:`;
        }
        dom.actionSearch.classList.remove('hidden');
        dom.nodeSearchInput.focus();
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    UI.attachAutocomplete(
        dom.nodeSearchInput, dom.nodeAutocomplete,
        async (q) => {
            if (!state.selectedNodeId) return [];
            const node = graph.nodes.get(state.selectedNodeId);
            if (!node) return [];

            if (node.type === 'person') {
                return API.searchSongsForArtist(node.data.id, q);
            } else {
                // Filter from song's known contributors first
                const lower = q.toLowerCase();
                const local = (node.data.contributors || [])
                    .map(c => c.person)
                    .filter(p => p && p.name.toLowerCase().includes(lower));
                if (local.length > 0) return local;
                return API.searchPeople(q);
            }
        },
        (item) => {
            const node = graph.nodes.get(state.selectedNodeId);
            if (!node) return { primary: '', secondary: '' };
            return node.type === 'person' ? UI.songRenderItem(item) : UI.personRenderItem(item);
        },
        (item) => addFromSearch(item)
    );

    function addFromSearch(item) {
        if (!state.selectedNodeId) return;
        const selected = graph.nodes.get(state.selectedNodeId);
        if (!selected) return;

        if (selected.type === 'person') {
            // Adding a song
            if (!graph.nodes.has(item.id)) {
                const { x, y } = findPosition(selected.id);
                addNodeToGraph('song', item, x, y);
            }
            addEdgeToGraph(selected.id, item.id);
        } else {
            // Adding a person to a song
            if (!graph.nodes.has(item.id)) {
                const { x, y } = findPosition(selected.id);
                addNodeToGraph('person', item, x, y);
            }
            addEdgeToGraph(item.id, selected.id);
        }

        dom.nodeSearchInput.value = '';
        dom.nodeAutocomplete.classList.remove('open');
        dom.nodeAutocomplete.innerHTML = '';

        updateNodeCount();
        checkWin();
    }

    // ─── Win Detection ────────────────────────────────────────────────────────

    function checkWin() {
        if (state.complete) return;
        const path = graph.findPath(state.startPerson.id, state.endPerson.id);
        if (path) {
            state.complete = true;
            highlightPath(path);
            setTimeout(() => showResult(path, false), 900);
        }
    }

    function highlightPath(path) {
        for (const id of path) {
            state.nodeElements.get(id)?.classList.add('graph-node--winner');
        }
        for (let i = 0; i < path.length - 1; i++) {
            const a = path[i], b = path[i + 1];
            const line = state.edgeElements.get(`${a}|${b}`) ||
                         state.edgeElements.get(`${b}|${a}`);
            line?.classList.add('graph-edge--winner');
        }
    }

    // ─── Hint ─────────────────────────────────────────────────────────────────

    dom.hintBtn.addEventListener('click', async () => {
        if (!state.selectedNodeId || state.complete) return;
        const node = graph.nodes.get(state.selectedNodeId);
        if (!node) return;

        UI.showLoading('Finding best connection…');
        try {
            if (node.type === 'person') {
                await hintForPerson(node);
            } else {
                await hintForSong(node);
            }
        } catch (e) {
            console.error('Hint error:', e);
        }
        UI.hideLoading();
        updateNodeCount();
        checkWin();
    });

    // For a person: use BFS to find the optimal next song toward the end person
    async function hintForPerson(node) {
        const result = await API.findChain(node.data.id, state.endPerson.id);
        if (!result.found || !result.steps?.length) return;

        const song = result.steps[0].viaSong;
        if (!graph.nodes.has(song.id)) {
            const { x, y } = findPosition(node.id);
            addNodeToGraph('song', song, x, y);
        }
        addEdgeToGraph(node.id, song.id);
    }

    // For a song: add the most useful contributor not yet on the board
    async function hintForSong(node) {
        const result = await API.getSongContributors(node.data.id);
        const contributors = result.contributors || {};
        const existingIds = new Set(graph.personIds());

        const newId = Object.keys(contributors).find(id => !existingIds.has(id));
        if (!newId) return;

        const person = await API.getArtist(newId);
        if (!person) return;

        if (!graph.nodes.has(person.id)) {
            const { x, y } = findPosition(node.id);
            addNodeToGraph('person', person, x, y);
        }
        addEdgeToGraph(person.id, node.data.id);
    }

    // ─── Give Up ─────────────────────────────────────────────────────────────

    dom.giveUpBtn.addEventListener('click', async () => {
        if (state.complete) return;
        UI.showLoading('Finding shortest chain…');
        try {
            const result = await API.findChain(state.startPerson.id, state.endPerson.id);
            UI.hideLoading();
            if (!result.found) return;

            // Add all missing nodes and edges from the solution
            for (let i = 0; i < result.steps.length; i++) {
                const fromPerson = result.personChain[i];
                const toPerson   = result.personChain[i + 1];
                const song       = result.steps[i].viaSong;

                if (!graph.nodes.has(fromPerson.id)) {
                    const { x, y } = i === 0
                        ? { x: 100, y: GRAPH_H / 2 }
                        : findPosition(song.id);
                    addNodeToGraph('person', fromPerson, x, y);
                }
                if (!graph.nodes.has(song.id)) {
                    const { x, y } = findPosition(fromPerson.id);
                    addNodeToGraph('song', song, x, y);
                }
                if (!graph.nodes.has(toPerson.id)) {
                    const { x, y } = findPosition(song.id);
                    addNodeToGraph('person', toPerson, x, y);
                }
                addEdgeToGraph(fromPerson.id, song.id);
                addEdgeToGraph(toPerson.id, song.id);
            }

            updateNodeCount();
            state.complete = true;
            const path = graph.findPath(state.startPerson.id, state.endPerson.id);
            if (path) {
                highlightPath(path);
                setTimeout(() => showResult(path, true), 900);
            }
        } catch (e) {
            UI.hideLoading();
        }
    });

    // ─── Result ───────────────────────────────────────────────────────────────

    function showResult(path, isAutoSolve) {
        dom.gameSection.classList.add('hidden');
        dom.resultSection.classList.remove('hidden');

        const songCount = path.filter(id => graph.nodes.get(id)?.type === 'song').length;
        const totalNodes = graph.nodes.size;

        dom.resultEmoji.textContent = isAutoSolve ? '🔍' : (songCount === 1 ? '🏆' : '🎵');
        dom.resultTitle.textContent = isAutoSolve ? 'Solution!' : 'Connected!';
        dom.resultSubtitle.textContent = isAutoSolve
            ? `Shortest path is ${songCount} song${songCount === 1 ? '' : 's'} long.`
            : `Connected in ${songCount} song${songCount === 1 ? '' : 's'} using ${totalNodes} nodes total.`;

        dom.resultChain.innerHTML = '';
        for (const id of path) {
            const node = graph.nodes.get(id);
            if (!node) continue;
            const el = document.createElement('div');
            if (node.type === 'person') {
                const isEndpoint = id === state.startPerson.id || id === state.endPerson.id;
                el.className = 'result-person-pill' + (isEndpoint ? ' endpoint' : '');
                el.textContent = node.data.name;
            } else {
                el.className = 'result-song-connector';
                el.innerHTML = `
                    <div class="line"></div>
                    <div class="song-name">♪ ${UI.escapeHtml(node.data.title)}
                        ${node.data.releaseYear ? '(' + UI.escapeHtml(node.data.releaseYear) + ')' : ''}
                    </div>
                    <div class="line"></div>
                `;
            }
            dom.resultChain.appendChild(el);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    function updateNodeCount() {
        dom.nodeCount.textContent = graph.nodes.size;
    }

    function resetToSetup() {
        state.phase = 'setup';
        state.startPerson = null;
        state.endPerson = null;
        state.selectedNodeId = null;
        state.complete = false;
        graph.clear();
        state.nodeElements.clear();
        state.edgeElements.clear();

        dom.graphNodes.innerHTML = '';
        dom.graphSvg.innerHTML = '';

        [dom.startSelected, dom.endSelected].forEach(el => el.classList.add('hidden'));
        [dom.startInput, dom.endInput].forEach(el => { el.style.display = ''; el.value = ''; });
        dom.startGameBtn.disabled = true;

        dom.resultSection.classList.add('hidden');
        dom.gameSection.classList.add('hidden');
        dom.setupSection.classList.remove('hidden');
    }

    // NODE_W/NODE_H used for centre-point calculations
const NODE_W = 148;
const NODE_H = 52; // approximate — tall enough for most labels

function makeDraggable(el, nodeId) {
    let dragging = false;
    let moved    = false;
    let startMouseX, startMouseY, startLeft, startTop;

    el.addEventListener('mousedown', (e) => {
        if (e.button !== 0) return;
        dragging    = true;
        moved       = false;
        startMouseX = e.clientX;
        startMouseY = e.clientY;
        startLeft   = parseFloat(el.style.left) || 0;
        startTop    = parseFloat(el.style.top)  || 0;
        el.style.zIndex = '200';
        e.preventDefault(); // prevent text selection
    });

    document.addEventListener('mousemove', (e) => {
        if (!dragging) return;
        const dx = e.clientX - startMouseX;
        const dy = e.clientY - startMouseY;

        if (Math.abs(dx) > 4 || Math.abs(dy) > 4) moved = true;
        if (!moved) return;

        // Clamp inside graph canvas
        const newLeft = Math.max(0,               Math.min(GRAPH_W - NODE_W, startLeft + dx));
        const newTop  = Math.max(0,               Math.min(GRAPH_H - NODE_H, startTop  + dy));

        el.style.left = newLeft + 'px';
        el.style.top  = newTop  + 'px';

        // Keep the graph data in sync
        const node = graph.nodes.get(nodeId);
        if (node) {
            node.x = newLeft + NODE_W / 2;
            node.y = newTop  + NODE_H / 2;
        }

        refreshEdgesForNode(nodeId);
    });

    document.addEventListener('mouseup', () => {
        if (!dragging) return;
        dragging = false;
        el.style.zIndex = '';

        if (!moved) {
            // Short tap with no movement = select
            selectNode(nodeId);
        } else {
            // After drag: nudge apart any nodes that now overlap
            resolveOverlaps();
        }
    });
}

function refreshEdgesForNode(nodeId) {
    for (const [key, line] of state.edgeElements) {
        const [p, s] = key.split('|');
        if (p === nodeId || s === nodeId) refreshLine(line, p, s);
    }
}

function resolveOverlaps() {
    const nodes = [...graph.nodes.values()];
    let changed = true;
    let passes  = 0;

    while (changed && passes < 10) {
        changed = false;
        passes++;
        for (let i = 0; i < nodes.length; i++) {
            for (let j = i + 1; j < nodes.length; j++) {
                const a = nodes[i], b = nodes[j];
                const dx = b.x - a.x, dy = b.y - a.y;
                const dist = Math.sqrt(dx * dx + dy * dy);
                const minDist = NODE_MIN_GAP;

                if (dist < minDist && dist > 0) {
                    const push = (minDist - dist) / 2 + 1;
                    const nx = dx / dist, ny = dy / dist;

                    // Only push nodes that aren't being dragged (z-index 200)
                    const elA = state.nodeElements.get(a.id);
                    const elB = state.nodeElements.get(b.id);
                    const aLocked = elA?.style.zIndex === '200';
                    const bLocked = elB?.style.zIndex === '200';

                    if (!aLocked) {
                        a.x = Math.max(NODE_W / 2,       Math.min(GRAPH_W - NODE_W / 2, a.x - nx * push));
                        a.y = Math.max(NODE_H / 2,       Math.min(GRAPH_H - NODE_H / 2, a.y - ny * push));
                        if (elA) {
                            elA.style.left = (a.x - NODE_W / 2) + 'px';
                            elA.style.top  = (a.y - NODE_H / 2) + 'px';
                        }
                    }
                    if (!bLocked) {
                        b.x = Math.max(NODE_W / 2,       Math.min(GRAPH_W - NODE_W / 2, b.x + nx * push));
                        b.y = Math.max(NODE_H / 2,       Math.min(GRAPH_H - NODE_H / 2, b.y + ny * push));
                        if (elB) {
                            elB.style.left = (b.x - NODE_W / 2) + 'px';
                            elB.style.top  = (b.y - NODE_H / 2) + 'px';
                        }
                    }
                    changed = true;
                }
            }
        }
    }

    // Refresh all edges after settling
    for (const [key, line] of state.edgeElements) {
        const [p, s] = key.split('|');
        refreshLine(line, p, s);
    }
}

    dom.restartBtn.addEventListener('click', resetToSetup);
    dom.playAgainBtn.addEventListener('click', resetToSetup);

})();