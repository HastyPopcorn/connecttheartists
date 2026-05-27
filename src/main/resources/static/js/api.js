/**
 * api.js — All calls to the Music Chain Spring Boot backend.
 * The backend proxies everything to MusicBrainz (rate-limited, cached).
 */

const API = (() => {
    const BASE = '/api';

    async function request(path, options = {}) {
        const res = await fetch(BASE + path, {
            headers: { 'Content-Type': 'application/json' },
            ...options,
        });
        if (!res.ok) throw new Error(`API error ${res.status}: ${path}`);
        return res.json();
    }

    return {
        /**
         * Search people (artists, producers, etc.) by name.
         * @param {string} query
         * @returns {Promise<Person[]>}
         */
        searchPeople(query) {
            return request(`/search?q=${encodeURIComponent(query)}`);
        },

        /**
         * Get a single artist by MBID.
         * @param {string} id
         * @returns {Promise<Person>}
         */
        getArtist(id) {
            return request(`/artist/${id}`);
        },

        /**
         * Get all songs a person contributed to.
         * @param {string} artistId
         * @returns {Promise<Song[]>}
         */
        getArtistSongs(artistId) {
            return request(`/artist/${artistId}/songs`);
        },

        searchSongs(query) {
            return request(`/song/search?q=${encodeURIComponent(query)}`);
        },

        searchSongsForArtist(artistId, query) {
            return request(`/artist/${artistId}/song-search?q=${encodeURIComponent(query)}`);
        },

        /**
         * Get all contributors for a song.
         * @param {string} songId
         * @returns {Promise<{song: Song, contributors: Record<string, string>}>}
         */
        getSongContributors(songId) {
            return request(`/song/${songId}/contributors`);
        },

        /**
         * Validate a player's chain step.
         * @param {string} fromPersonId
         * @param {string} songId
         * @param {string} toPersonId
         * @returns {Promise<{valid: boolean, message: string, fromRole: string, toRole: string}>}
         */
        validateStep(fromPersonId, songId, toPersonId) {
            return request('/chain/validate-step', {
                method: 'POST',
                body: JSON.stringify({ fromPersonId, songId, toPersonId }),
            });
        },

        /**
         * Auto-solve: find the shortest chain between two people.
         * @param {string} startId
         * @param {string} endId
         * @returns {Promise<ChainResult>}
         */
        findChain(startId, endId) {
            return request('/chain/find', {
                method: 'POST',
                body: JSON.stringify({ startId, endId }),
            });
        },
        getBandMembers(artistId) {
            return request(`/artist/${artistId}/members`);
      },
    };

    
})();

// ─── Type definitions (JSDoc) ─────────────────────────────────────────────
/**
 * @typedef {Object} Person
 * @property {string} id
 * @property {string} name
 * @property {string} type
 * @property {string} disambiguation
 */

/**
 * @typedef {Object} Song
 * @property {string} id
 * @property {string} title
 * @property {string} releaseYear
 * @property {string} albumName
 * @property {Contributor[]} contributors
 */

/**
 * @typedef {Object} ChainResult
 * @property {Person} startPerson
 * @property {Person} endPerson
 * @property {ChainStep[]} steps
 * @property {Person[]} personChain
 * @property {number} chainLength
 * @property {boolean} found
 * @property {string} message
 */
