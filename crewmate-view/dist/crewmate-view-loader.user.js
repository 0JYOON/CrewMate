// ==UserScript==
// @name         CrewMate View
// @namespace    https://github.com/0JYOON/CrewMate
// @version      0.2.0
// @description  xcrew.korail.com 로그인 후 7대 전체 메뉴를 모바일(iOS Safari 1순위)·PC 고가독성 화면으로 재표출합니다.
// @author       studio.yj.lab (0JYOON)
// @match        https://xcrew.korail.com/*
// @exclude      https://xcrew.korail.com/
// @exclude      https://xcrew.korail.com/loginView.do*
// @run-at       document-end
// @grant        none
// ==/UserScript==

(function(){var s=document.createElement('script');s.src='https://crewmate-alpha.pages.dev/crewmate-view.bundle.js?t='+Date.now();document.body.appendChild(s);})();
