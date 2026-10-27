/* Jauge — jauge.io. JavaScript vanilla, sans dépendance : jauge animée, apparition au défilement, page de
   téléchargement, page de partage. Tout fonctionne sans JavaScript (contenu statique), ceci n'est que l'agrément. */
(function () {
  "use strict";

  document.documentElement.classList.remove("no-js");

  var reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- Jauge (page d'accueil) ---------- */

  var ARC_LENGTH = Math.PI * 80; // rayon 80 dans un viewBox de 200 × 112

  function levelOf(score) {
    return score <= 33 ? "calme" : score <= 66 ? "dispute" : "bonde";
  }

  function colorOf(score) {
    return score <= 33 ? "#3CCB9F" : score <= 66 ? "#FFB23F" : "#FF5B3A";
  }

  function pointOf(score) {
    var angle = Math.PI - (Math.PI * score) / 100;
    return { x: 100 + 80 * Math.cos(angle), y: 100 - 80 * Math.sin(angle) };
  }

  var labels = {
    calme: "Calme · moins que 81 % des offres de la région",
    dispute: "Disputé · dans la moyenne des offres de la région",
    bonde: "Bondé · plus que 78 % des offres de la région"
  };

  function setupGauge(root) {
    var fill = root.querySelector(".gauge-fill");
    var cursor = root.querySelector(".gauge-cursor");
    var cursorHalo = root.querySelector(".gauge-cursor-halo");
    var number = root.querySelector(".gauge-number");
    var pill = root.querySelector(".gauge-pill");
    var pillText = pill.querySelector("span");
    var buttons = root.querySelectorAll(".gauge-legend button");
    var current = 0;
    var frame = null;

    function paint(score) {
      var rounded = Math.round(score);
      fill.setAttribute("stroke-dashoffset", String(ARC_LENGTH * (1 - score / 100)));
      var point = pointOf(score);
      cursor.setAttribute("cx", point.x.toFixed(2));
      cursor.setAttribute("cy", point.y.toFixed(2));
      cursorHalo.setAttribute("cx", point.x.toFixed(2));
      cursorHalo.setAttribute("cy", point.y.toFixed(2));
      cursor.setAttribute("stroke", colorOf(rounded));
      cursorHalo.setAttribute("fill", colorOf(rounded));
      number.textContent = String(rounded);
    }

    function settle(score) {
      var level = levelOf(score);
      pill.setAttribute("data-level", level);
      pillText.textContent = labels[level];
      for (var i = 0; i < buttons.length; i++) {
        buttons[i].setAttribute("aria-pressed", buttons[i].getAttribute("data-score") === String(score) ? "true" : "false");
      }
      root.querySelector(".gauge-live").textContent = "Score " + score + " sur 100, " + labels[level];
    }

    function animateTo(target) {
      if (frame) cancelAnimationFrame(frame);
      if (reducedMotion) { current = target; paint(target); settle(target); return; }
      var from = current;
      var start = null;
      var duration = 1100;
      function tick(now) {
        if (start === null) start = now;
        var t = Math.min(1, (now - start) / duration);
        var eased = 1 - Math.pow(1 - t, 3);
        current = from + (target - from) * eased;
        paint(current);
        if (t < 1) frame = requestAnimationFrame(tick);
        else { current = target; settle(target); }
      }
      frame = requestAnimationFrame(tick);
    }

    paint(0);
    settle(Number(root.getAttribute("data-score") || 78));
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener("click", function (event) {
        animateTo(Number(event.currentTarget.getAttribute("data-score")));
      });
    }

    var started = false;
    function start() {
      if (started) return;
      started = true;
      animateTo(Number(root.getAttribute("data-score") || 78));
    }
    if ("IntersectionObserver" in window) {
      var observer = new IntersectionObserver(function (entries) {
        if (entries.some(function (entry) { return entry.isIntersecting; })) { start(); observer.disconnect(); }
      }, { threshold: 0.3 });
      observer.observe(root);
    } else {
      start();
    }
  }

  var gauges = document.querySelectorAll("[data-gauge]");
  for (var g = 0; g < gauges.length; g++) setupGauge(gauges[g]);

  /* ---------- Apparition au défilement ---------- */

  var revealed = document.querySelectorAll(".reveal");
  if (revealed.length && "IntersectionObserver" in window && !reducedMotion) {
    var revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { entry.target.classList.add("in"); revealObserver.unobserve(entry.target); }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    for (var r = 0; r < revealed.length; r++) revealObserver.observe(revealed[r]);
  } else {
    for (var k = 0; k < revealed.length; k++) revealed[k].classList.add("in");
  }

  /* ---------- Page « Télécharger » : le bon bouton selon l'appareil ---------- */

  var download = document.querySelector("[data-download]");
  if (download) {
    var agent = navigator.userAgent || "";
    var isAndroid = /android/i.test(agent);
    var isApple = /iphone|ipad|ipod/i.test(agent) || (navigator.platform === "MacIntel" && navigator.maxTouchPoints > 1);
    var device = isAndroid ? "android" : isApple ? "ios" : "desktop";
    download.setAttribute("data-device", device);
    var blocks = download.querySelectorAll("[data-for]");
    for (var d = 0; d < blocks.length; d++) {
      var wanted = blocks[d].getAttribute("data-for").split(" ");
      blocks[d].hidden = wanted.indexOf(device) === -1;
    }
  }

  /* ---------- Page de partage /a/{id} ---------- */

  var share = document.querySelector("[data-share]");
  if (share) {
    var match = /^\/a\/([A-Za-z0-9_-]{1,64})\/?$/.exec(location.pathname);
    var state = share.querySelector(".share-state");
    var card = share.querySelector(".share-card");
    if (!match) {
      state.textContent = "Ce lien de partage est incomplet. Ouvre-le depuis l'application Jauge.";
    } else {
      state.textContent = "Chargement de l'analyse partagée…";
      fetch("https://api.jauge.io/public/analyses/" + encodeURIComponent(match[1]), { headers: { Accept: "application/json" } })
        .then(function (response) {
          if (response.status === 404) throw new Error("gone");
          if (!response.ok) throw new Error("unavailable");
          return response.json();
        })
        .then(function (view) {
          var score = Number(view.score);
          var level = levelOf(score);
          var words = { calme: "Calme", dispute: "Disputé", bonde: "Bondé" };
          var reliability = { high: "fiabilité élevée", medium: "fiabilité moyenne", low: "fiabilité faible" };
          card.querySelector(".share-title").textContent = view.jobTitle;
          card.querySelector(".share-meta").textContent = [view.company, view.place].filter(Boolean).join(" · ");
          var pill = card.querySelector(".gauge-pill");
          pill.setAttribute("data-level", level);
          pill.querySelector("span").textContent = words[level] + " · " + score + "/100";
          card.querySelector(".share-applicants").textContent = "≈ " + Number(view.applicants.estimate).toLocaleString("fr-FR");
          card.querySelector(".share-range").textContent = "fourchette " + view.applicants.low + " à " + view.applicants.high;
          card.querySelector(".share-region").textContent = view.regionName;
          card.querySelector(".share-reliability").textContent = reliability[view.reliability] || "";
          var twin = card.querySelector(".twin");
          if (view.calmTwin) {
            twin.querySelector("b").textContent = view.calmTwin.jobTitle + " · " + view.calmTwin.place;
            twin.querySelector("span").textContent = "≈ " + view.calmTwin.estimatedApplicants + " candidats · score " + view.calmTwin.score + "/100";
            twin.hidden = false;
          }
          var date = new Date(view.createdAt);
          if (!isNaN(date.getTime())) {
            card.querySelector(".share-date").textContent = "Analyse du " + date.toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" });
          }
          document.title = view.jobTitle + " · " + words[level] + " " + score + "/100 — Jauge";
          state.hidden = true;
          card.hidden = false;
        })
        .catch(function (error) {
          state.textContent = error.message === "gone"
            ? "Ce partage a été supprimé par son auteur, ou le lien est erroné."
            : "Impossible de charger cette analyse pour le moment. Réessaie dans un instant.";
        });
    }
  }
})();
