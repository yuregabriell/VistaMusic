"use strict";
(function () {
  var $ = function (id) { return document.getElementById(id); };
  var audio = $("audio");
  var STORAGE = "vistamusic-web-v1";
  var state = load();
  var catalog = [];
  var searchTracks = [];
  var queue = [];
  var index = -1;
  var current = null;
  var selection = null;
  var selectedPlaylist = null;
  var noticeTimeout = 0;
  var tab = "home";
  var fetching = false;

  function load() {
    try {
      var parsed = JSON.parse(localStorage.getItem(STORAGE) || "{}");
      return {
        favorites: Array.isArray(parsed.favorites) ? parsed.favorites : [],
        recent: Array.isArray(parsed.recent) ? parsed.recent : [],
        playlists: Array.isArray(parsed.playlists) ? parsed.playlists : []
      };
    } catch (_) { return { favorites: [], recent: [], playlists: [] }; }
  }
  function save() {
    try { localStorage.setItem(STORAGE, JSON.stringify(state)); }
    catch (_) { toast("Não foi possível guardar a biblioteca neste dispositivo."); }
  }
  function escapeHTML(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c];
    });
  }
  function urlHTML(value) {
    var s = String(value || "");
    return /^https:\/\//.test(s) ? escapeHTML(s) : "icon.svg";
  }
  function showMessage(text, error) {
    return '<div class="center">' + escapeHTML(text) + (error ? '<br><br><button class="btn" data-action="refresh">Tentar novamente</button>' : "") + "</div>";
  }
  function toast(message) {
    var el = $("toast");
    el.textContent = message;
    el.style.display = "block";
    clearTimeout(noticeTimeout);
    noticeTimeout = setTimeout(function () { el.style.display = "none"; }, 3700);
  }
  function audiusTrack(item) {
    if (!item || !item.id || !item.title || item.is_streamable === false ||
        item.is_available === false || item.is_delete === true) return null;
    var picture = item.artwork || {};
    return {
      id: "audius:" + item.id,
      audiusId: String(item.id),
      title: String(item.title),
      artist: String((item.user && (item.user.name || item.user.handle)) || "Artista"),
      art: String(picture["480x480"] || picture["150x150"] || ""),
      source: "Audius · faixa completa",
      stream: "https://api.audius.co/v1/tracks/" + encodeURIComponent(item.id) + "/stream?app_name=VistaMusicWeb",
      url: (typeof item.permalink === "string" && /^https:\/\//.test(item.permalink)) ? item.permalink : ""
    };
  }
  async function audius(endpoint) {
    var sources = ["https://api.audius.co/v1/", "https://discoveryprovider.audius.co/v1/"];
    var error;
    for (var i = 0; i < sources.length; i++) {
      try {
        var res = await fetch(sources[i] + endpoint + (endpoint.indexOf("?") < 0 ? "?" : "&") + "app_name=VistaMusicWeb", { mode: "cors" });
        if (!res.ok) throw new Error("Audius HTTP " + res.status);
        var json = await res.json();
        if (!json || !Array.isArray(json.data)) throw new Error("Resposta inválida");
        return json.data.map(audiusTrack).filter(Boolean);
      } catch (e) { error = e; }
    }
    throw error || new Error("Serviço indisponível");
  }
  function art(track, extra) {
    return '<img loading="lazy" alt="" src="' + urlHTML(track.art) + '" ' + (extra || "") + '>';
  }
  function tile(t) {
    return '<button class="tile" data-action="play" data-id="' + escapeHTML(t.id) + '">' +
      art(t) + '<span class="title">' + escapeHTML(t.title) + '</span><span class="artist">' +
      escapeHTML(t.artist) + '</span></button>';
  }
  function row(t) {
    var liked = isLiked(t);
    return '<div class="song">' + art(t, 'data-action="play" data-id="' + escapeHTML(t.id) + '"') +
      '<div class="details" data-action="play" data-id="' + escapeHTML(t.id) + '"><strong>' +
      escapeHTML(t.title) + '</strong><span>' + escapeHTML(t.artist) + ' · ' +
      escapeHTML(t.source) + '</span></div>' +
      '<button aria-label="Curtir" class="subbtn ' + (liked ? "liked" : "") +
      '" data-action="like" data-id="' + escapeHTML(t.id) + '">' + (liked ? "♥" : "♡") + '</button>' +
      '<button aria-label="Adicionar a playlist" class="subbtn" data-action="add" data-id="' +
      escapeHTML(t.id) + '">⋯</button></div>';
  }
  function section(title, tracks) {
    if (!tracks.length) return "";
    return '<div class="section"><h2>' + escapeHTML(title) + '</h2><div class="hscroll">' +
      tracks.slice(0, 25).map(tile).join("") + "</div></div>";
  }
  function allKnown() {
    var arr = [].concat(catalog, searchTracks, state.recent, state.favorites);
    state.playlists.forEach(function (p) { arr = arr.concat(p.tracks || []); });
    if (current) arr.push(current);
    var seen = {};
    return arr.filter(function (t) {
      if (!t || !t.id || seen[t.id]) return false;
      seen[t.id] = true; return true;
    });
  }
  function findTrack(id) { return allKnown().find(function (t) { return t.id === id; }); }
  function isLiked(track) { return !!state.favorites.find(function (t) { return t.id === track.id; }); }
  function recommend(tracks) {
    var loved = {};
    state.favorites.concat(state.recent.slice(0, 10)).forEach(function (t) {
      loved[t.artist.toLowerCase()] = (loved[t.artist.toLowerCase()] || 0) + 1;
    });
    return tracks.slice().sort(function (a, b) {
      return (loved[b.artist.toLowerCase()] || 0) - (loved[a.artist.toLowerCase()] || 0);
    });
  }
  function renderHome(message) {
    var featured = recommend(catalog)[0] || state.recent[0] || state.favorites[0];
    if (featured) {
      $("heroTitle").textContent = featured.title;
      $("heroArtist").textContent = featured.artist;
      $("heroArt").src = featured.art || "icon.svg";
      $("hero").dataset.id = featured.id;
      $("hero").style.opacity = "1";
    } else {
      $("heroTitle").textContent = "A tua música está aqui";
      $("heroArtist").textContent = "Descobre músicas, artistas e novas playlists";
      $("hero").removeAttribute("data-id");
      $("hero").style.opacity = ".85";
    }
    $("homeSections").innerHTML =
      (state.recent.length ? section("Ouvidas recentemente", state.recent) : "") +
      section("Sons para descobrir", recommend(catalog)) +
      section("Em alta agora · Audius", catalog) +
      section("Músicas curtidas", state.favorites) +
      (message ? showMessage(message, true) : "");
  }
  function renderLibrary() {
    var cards = '<button class="listcard" data-action="list" data-id="liked"><div class="square">♥</div><div><b>Músicas curtidas</b><small>' +
      state.favorites.length + ' músicas</small></div></button>';
    state.playlists.forEach(function (p) {
      cards += '<button class="listcard" data-action="list" data-id="' + escapeHTML(p.id) +
        '"><div class="square">♫</div><div><b>' + escapeHTML(p.name) + '</b><small>' +
        p.tracks.length + ' músicas</small></div></button>';
    });
    $("playlistCards").innerHTML = cards;
    if (selectedPlaylist) {
      var pl = state.playlists.find(function (p) { return p.id === selectedPlaylist; });
      var songs = selectedPlaylist === "liked" ? state.favorites : (pl ? pl.tracks : []);
      var name = selectedPlaylist === "liked" ? "Músicas curtidas" : (pl ? pl.name : "Playlist");
      $("libraryTracks").innerHTML =
        '<div class="section"><h2>' + escapeHTML(name) + '</h2>' +
        (pl ? '<div class="hint"><button class="btn" data-action="deleteList" data-id="' + escapeHTML(pl.id) +
          '">Eliminar playlist</button></div>' : "") +
        '<div class="rowlist">' + (songs.length ? songs.map(row).join("") :
          '<p class="muted">Esta lista ainda não tem músicas.</p>') + '</div></div>';
    } else {
      $("libraryTracks").innerHTML =
        state.recent.length ? '<div class="section"><h2>Histórico</h2><div class="rowlist">' +
        state.recent.slice(0, 14).map(row).join("") + '</div></div>' : "";
    }
  }
  function renderSearch(message) {
    $("searchResults").innerHTML = searchTracks.length ?
      '<div class="section"><h2>Resultados · Audius</h2><div class="rowlist">' +
      searchTracks.map(row).join("") + '</div></div>' :
      (message ? showMessage(message, false) : "");
  }
  async function refresh() {
    if (fetching) return;
    fetching = true;
    $("refresh").textContent = "…";
    renderHome("A carregar músicas...");
    try {
      catalog = await audius("tracks/trending?limit=45");
      renderHome(catalog.length ? "" : "Não há faixas disponíveis nesta fonte.");
    } catch (e) {
      renderHome("Não foi possível consultar o Audius neste momento. Confirma a ligação e tenta novamente.");
    } finally {
      fetching = false;
      $("refresh").textContent = "↻";
    }
  }
  async function search(term) {
    if (!term.trim()) return;
    $("searchResults").innerHTML = showMessage("A pesquisar músicas...");
    $("youtubeSearch").href = "https://music.youtube.com/search?q=" + encodeURIComponent(term);
    try {
      searchTracks = await audius("tracks/search?query=" + encodeURIComponent(term) + "&limit=40");
      renderSearch(searchTracks.length ? "" : "Nenhuma música encontrada nesta fonte.");
    } catch (e) {
      searchTracks = [];
      renderSearch("A pesquisa Audius está temporariamente indisponível.");
    }
  }
  function setTab(next) {
    tab = next;
    document.querySelectorAll(".view").forEach(function (v) { v.classList.toggle("active", v.id === next); });
    document.querySelectorAll(".tabs button").forEach(function (b) { b.classList.toggle("active", b.dataset.tab === next); });
    if (next === "library") renderLibrary();
    window.scrollTo(0, 0);
  }
  function playTrack(track, list) {
    if (!track) return;
    queue = (list && list.length ? list : allKnown()).filter(function (t) { return t.stream; });
    index = queue.findIndex(function (t) { return t.id === track.id; });
    if (index < 0) { queue.unshift(track); index = 0; }
    begin();
  }
  function rememberPlay(t) {
    state.recent = [t].concat(state.recent.filter(function (v) { return v.id !== t.id; })).slice(0, 80);
    save();
    renderHome();
    renderLibrary();
  }
  function begin() {
    current = queue[index];
    if (!current) return;
    audio.pause();
    audio.src = current.stream;
    audio.load();
    audio.play().catch(function () {
      toast("Não foi possível reproduzir esta faixa. Verifica a fonte ou escolhe outra.");
    });
    rememberPlay(current);
    updatePlayer();
    if ("mediaSession" in navigator) {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: current.title,
        artist: current.artist,
        album: "VistaMusic · Audius",
        artwork: current.art && /^https:/.test(current.art) ?
          [{ src: current.art, sizes: "512x512", type: "image/jpeg" }] : []
      });
      ["play", "pause", "previoustrack", "nexttrack"].forEach(function (act) {
        try {
          navigator.mediaSession.setActionHandler(act, function () {
            if (act === "play") audio.play().catch(function () {});
            else if (act === "pause") audio.pause();
            else if (act === "previoustrack") previous();
            else if (act === "nexttrack") next();
          });
        } catch (_) {}
      });
    }
  }
  function updatePlayer() {
    if (!current) return;
    $("miniplayer").classList.add("show");
    $("miniArt").src = current.art || "icon.svg";
    $("miniTitle").textContent = current.title;
    $("miniArtist").textContent = current.artist;
    $("bigArt").src = current.art || "icon.svg";
    $("bigTitle").textContent = current.title;
    $("bigArtist").textContent = current.artist;
    $("bigSource").textContent = current.source;
    $("miniLike").textContent = isLiked(current) ? "♥" : "♡";
    $("playerLike").textContent = isLiked(current) ? "♥ Curtida" : "♡ Curtir";
    $("miniToggle").textContent = audio.paused ? "▶" : "⏸";
    $("toggle").textContent = audio.paused ? "▶" : "⏸";
    renderProgress();
    loadLyrics(current);
  }
  function toggle() {
    if (!current) { toast("Seleciona uma música primeiro."); return; }
    if (audio.paused) audio.play().catch(function () { toast("Reprodução indisponível."); });
    else audio.pause();
  }
  function next() {
    if (!queue.length) return;
    if (index + 1 >= queue.length) { audio.pause(); audio.currentTime = 0; return; }
    index++; begin();
  }
  function previous() {
    if (!queue.length) return;
    if (audio.currentTime > 3) { audio.currentTime = 0; return; }
    index = Math.max(0, index - 1); begin();
  }
  function fmt(t) {
    if (!Number.isFinite(t)) return "0:00";
    t = Math.max(0, Math.floor(t));
    return Math.floor(t / 60) + ":" + String(t % 60).padStart(2, "0");
  }
  function renderProgress() {
    var d = audio.duration || 0;
    var v = audio.currentTime || 0;
    $("seek").max = d || 100;
    $("seek").value = v;
    $("elapsed").textContent = fmt(v);
    $("duration").textContent = fmt(d);
    $("miniProgress").style.width = (d ? Math.min(100, 100 * v / d) : 0) + "%";
  }
  function toggleLike(t) {
    if (!t) return;
    if (isLiked(t)) state.favorites = state.favorites.filter(function (v) { return v.id !== t.id; });
    else state.favorites.unshift(t);
    save();
    renderHome(); renderLibrary(); renderSearch();
    if (current) updateLikes();
  }
  function updateLikes() {
    $("miniLike").textContent = isLiked(current) ? "♥" : "♡";
    $("playerLike").textContent = isLiked(current) ? "♥ Curtida" : "♡ Curtir";
  }
  function modal(title, body) {
    $("modalTitle").textContent = title;
    $("modalContent").innerHTML = body;
    $("modal").classList.add("show");
  }
  function closeModal() { $("modal").classList.remove("show"); }
  function addToPlaylist(t) {
    if (!t) return;
    selection = t;
    var buttons = state.playlists.map(function (p) {
      return '<button class="choice" data-action="savePlaylist" data-id="' + escapeHTML(p.id) +
        '">♫ ' + escapeHTML(p.name) + '</button>';
    }).join("");
    modal("Adicionar à playlist", buttons +
      '<button class="choice" data-action="createPlaylist">＋ Criar nova playlist</button>');
  }
  function createPlaylist() {
    modal("Nova playlist",
      '<form id="createPlaylistForm"><input id="playlistName" required maxlength="65" placeholder="Nome da playlist" autocomplete="off"><button class="btn accent" style="margin-top:13px;width:100%" type="submit">Criar playlist</button></form>');
    $("playlistName").focus();
    $("createPlaylistForm").addEventListener("submit", function (e) {
      e.preventDefault();
      var name = $("playlistName").value.trim();
      if (!name) return;
      var p = { id: "p" + Date.now().toString(36), name: name, tracks: selection ? [selection] : [] };
      state.playlists.unshift(p);
      save(); renderLibrary();
      selection = null; closeModal(); setTab("library"); selectedPlaylist = p.id; renderLibrary();
      toast("Playlist criada.");
    });
  }
  function saveToPlaylist(id) {
    var p = state.playlists.find(function (v) { return v.id === id; });
    if (!p || !selection) return;
    if (!p.tracks.some(function (v) { return v.id === selection.id; })) p.tracks.push(selection);
    save(); renderLibrary(); selection = null; closeModal();
    toast("Música guardada na playlist.");
  }
  function parseYouTubeId(raw) {
    try {
      var u = new URL(raw);
      var host = u.hostname.toLowerCase();
      var id = "";
      if (host === "youtu.be" || host === "www.youtu.be") id = u.pathname.slice(1).split("/")[0];
      else if (host === "youtube.com" || host === "www.youtube.com" || host === "m.youtube.com" ||
               host === "music.youtube.com") {
        id = u.searchParams.get("v") || "";
        if (!id && u.pathname.startsWith("/shorts/")) id = u.pathname.split("/")[2] || "";
      }
      return /^[A-Za-z0-9_-]{11}$/.test(id) ? id : "";
    } catch (_) { return ""; }
  }
  async function loadLyrics(track) {
    var id = track.id;
    var el = $("lyricsPanel");
    el.textContent = "A procurar letras disponibilizadas pela comunidade...";
    try {
      var u = "https://lrclib.net/api/get?artist_name=" + encodeURIComponent(track.artist) +
        "&track_name=" + encodeURIComponent(track.title);
      var result = await fetch(u, {mode:"cors"});
      if (!result.ok) throw new Error("Não encontrada");
      var data = await result.json();
      if (!current || current.id !== id) return;
      var lines = data.syncedLyrics || data.plainLyrics;
      if (!lines) throw new Error("Não encontrada");
      var clean = lines.replace(/\[\d{2}:\d{2}(?:\.\d+)?\]/g,"").trim();
      el.textContent = clean.slice(0, 5000) + "\n\nLetras: comunidade LRCLIB";
      el.style.whiteSpace = "pre-line";
    } catch (_) {
      if (current && current.id === id) el.textContent = "Sem letra disponível no LRCLIB para esta faixa.";
    }
  }

  document.addEventListener("click", function (event) {
    var control = event.target.closest("[data-action]");
    if (!control) return;
    var a = control.dataset.action;
    var id = control.dataset.id;
    if (a === "refresh") refresh();
    if (a === "play") {
      var t = findTrack(id);
      var context = control.closest("#searchResults") ? searchTracks :
        control.closest("#homeSections") ? catalog : allKnown();
      playTrack(t, context.filter(function (v) { return v.stream; }));
    }
    if (a === "like") toggleLike(findTrack(id));
    if (a === "add") addToPlaylist(findTrack(id));
    if (a === "list") {
      selectedPlaylist = id; setTab("library"); renderLibrary();
      $("libraryTracks").scrollIntoView({behavior:"smooth",block:"start"});
    }
    if (a === "createPlaylist") createPlaylist();
    if (a === "savePlaylist") saveToPlaylist(id);
    if (a === "deleteList") {
      if (confirm("Eliminar esta playlist?")) {
        state.playlists = state.playlists.filter(function (p) { return p.id !== id; });
        selectedPlaylist = null; save(); renderLibrary();
      }
    }
  });
  document.querySelectorAll(".tabs button").forEach(function (b) {
    b.addEventListener("click", function () { setTab(b.dataset.tab); });
  });
  $("refresh").addEventListener("click", refresh);
  $("searchForm").addEventListener("submit", function (e) {
    e.preventDefault(); search($("searchInput").value.trim());
  });
  $("youtubePlay").addEventListener("click", function () {
    var id = parseYouTubeId($("youtubeURL").value.trim());
    if (!id) { toast("Cola um link válido de vídeo do YouTube."); return; }
    $("youtubeEmbed").innerHTML = '<iframe title="Vídeo YouTube" allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen class="ytframe" src="https://www.youtube-nocookie.com/embed/' + id + '?playsinline=1"></iframe>';
    $("youtubeEmbed").scrollIntoView({behavior:"smooth"});
  });
  $("hero").addEventListener("click", function () {
    var id = $("hero").dataset.id;
    var t = findTrack(id);
    if (t) playTrack(t, catalog);
    else { setTab("search"); $("searchInput").focus(); }
  });
  $("miniOpen").addEventListener("click", function () { $("playerScreen").classList.add("show"); });
  $("miniArt").addEventListener("click", function () { $("playerScreen").classList.add("show"); });
  $("closePlayer").addEventListener("click", function () { $("playerScreen").classList.remove("show"); });
  $("miniToggle").addEventListener("click", toggle);
  $("toggle").addEventListener("click", toggle);
  $("prev").addEventListener("click", previous);
  $("next").addEventListener("click", next);
  $("seek").addEventListener("input", function () { if (audio.duration) audio.currentTime = Number($("seek").value); });
  $("miniLike").addEventListener("click", function () { toggleLike(current); });
  $("playerLike").addEventListener("click", function () { toggleLike(current); });
  $("playerAdd").addEventListener("click", function () { addToPlaylist(current); });
  $("newPlaylist").addEventListener("click", function () { selection = null; createPlaylist(); });
  $("likedList").addEventListener("click", function () { selectedPlaylist = "liked"; renderLibrary(); $("libraryTracks").scrollIntoView({behavior:"smooth"}); });
  $("closeModal").addEventListener("click", closeModal);
  $("modal").addEventListener("click", function (e) { if (e.target === $("modal")) closeModal(); });
  $("shareSong").addEventListener("click", function () {
    if (!current) return;
    var url = current.url || "https://audius.co/";
    if (navigator.share) navigator.share({title:current.title + " — " + current.artist,url:url}).catch(function () {});
    else { navigator.clipboard.writeText(url).then(function () {toast("Link copiado.");}).catch(function () {}); }
  });
  $("dismissBanner").addEventListener("click", function () {
    $("installBanner").style.display = "none"; localStorage.setItem("vmweb-banner-hidden","1");
  });
  if (localStorage.getItem("vmweb-banner-hidden") === "1" ||
      window.matchMedia("(display-mode: standalone)").matches || navigator.standalone) {
    $("installBanner").style.display = "none";
  }
  audio.addEventListener("timeupdate", renderProgress);
  audio.addEventListener("durationchange", renderProgress);
  audio.addEventListener("play", function () { $("toggle").textContent = "⏸"; $("miniToggle").textContent = "⏸"; });
  audio.addEventListener("pause", function () { $("toggle").textContent = "▶"; $("miniToggle").textContent = "▶"; });
  audio.addEventListener("ended", next);
  audio.addEventListener("error", function () { if (current) toast("O áudio não pôde ser reproduzido pela fonte Audius."); });
  if ("serviceWorker" in navigator) window.addEventListener("load", function () {
    navigator.serviceWorker.register("./service-worker.js").catch(function () {});
  });
  renderHome(); renderLibrary(); refresh();
})();
