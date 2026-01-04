(ns nav
  (:require [reagent.core :as r]))

(defonce is-dark (r/atom (= "dark" (.getItem js/localStorage "theme"))))

(defn toggle-dark! []
  (swap! is-dark not)
  (let [dark? @is-dark]
    (.setItem js/localStorage "theme" (if dark? "dark" "light"))
    (if dark?
      (-> js/document .-body .-classList (.add "dark-mode"))
      (-> js/document .-body .-classList (.remove "dark-mode")))))

(when @is-dark
  (-> js/document .-body .-classList (.add "dark-mode")))

(defn navbar []
  [:div.app-header
   [:a.brand {:href "/" :style {:text-decoration "none" :color "var(--color-text)" :font-size "1.5rem" :font-weight "bold"}} "Aino Kallinen"]
   [:div.nav-links
    [:button.btn-dark {:on-click toggle-dark!} (if @is-dark "LIGHT" "DARK")]]])
