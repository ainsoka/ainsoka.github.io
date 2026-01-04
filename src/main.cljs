(ns main
  (:require [nav :refer [navbar]] [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]))

(println "Main.cljs RELOADED v13 (Final Logos)")

;; -- State --
(defonce posts (r/atom []))
(defonce loading (r/atom true))
;; Local state replaced by shared
(def is-dark nav/is-dark)
;; Apply immediately
(when @is-dark (-> js/document .-body .-classList (.add "dark-mode")))
(defonce error-msg (r/atom nil))
(defonce active-post (r/atom nil)) ;; For Modal
(defonce zoomed-image (r/atom nil)) ;; For Fullscreen Image

;; -- Interop --
(def Papa js/Papa)

;; -- Dark Mode --


;; -- Helpers --
(defn format-body [text]
  (when text
    (into [:div]
          (map-indexed
            (fn [idx paragraph]
              (when (seq (str/trim paragraph))
                [:p {:key idx :style {:margin-bottom "1em" :line-height "1.6"}} 
                 paragraph]))
            (str/split text #"\n")))))

;; -- Carousel Component --
(def instagram-images
  [{:src "visuals/drawing_1.jpg" :text "April 1, 2024. Sketching."}
   {:src "visuals/drawing_2.jpg" :text "April 5, 2024. Lines."}
   {:src "visuals/drawing_3.jpg" :text "April 7, 2024. Texture."}
   {:src "visuals/drawing_4.jpg" :text "April 10, 2024."}
   {:src "visuals/drawing_5.jpg" :text "April 12, 2024."}
   {:src "visuals/drawing_6.jpg" :text "April 14, 2024."}])

(defn carousel []
  (let [index (r/atom 0)]
    (fn []
      (let [images instagram-images
            count (count images)
            current (nth images @index)]
        [:div.carousel-container {:style {:text-align "center"}}
         [:div.carousel
          [:button.carousel-btn.prev {:on-click #(swap! index (fn [i] (mod (dec i) count)))} "❮"]
          [:div.carousel-slide
           [:img {:src (:src current)}]]
          [:button.carousel-btn.next {:on-click #(swap! index (fn [i] (mod (inc i) count)))} "❯"]
          [:div.carousel-indicators
           (doall (map-indexed (fn [i _]
                                 [:span.indicator {:key i 
                                                   :class (when (= i @index) "active")
                                                   :on-click #(reset! index i)}])
                               images))]]
         ;; Caption
         [:div.carousel-caption {:style {:margin-top "20px" :color "var(--color-text)" :font-style "italic"}}
          (:text current)]]))))

;; -- Components --

(defn fullscreen-image-view []
    [:div.fullscreen-overlay {:on-click #(reset! zoomed-image nil)}
     [:img.fullscreen-image {:src @zoomed-image}]])

(defn modal [post]
  (let [tags (when (seq (:tags post))
               (map str/trim (str/split (:tags post) #",")))
        extra-classes (if tags
                        (str/join " " (map #(str "tag-" (str/replace % #"\s+" "-")) tags))
                        "")]
    [:div.modal-overlay {:on-click #(reset! active-post nil)}
     [:div.modal-content {:class extra-classes 
                          :on-click #(.stopPropagation %)}
      [:button.close-btn {:on-click #(reset! active-post nil)} "×"]
      
      (cond 
        (seq (:video_url post))
        ;; If Video Exists: Show Video ONLY (no image)
        [:div.video-container
         {:dangerouslySetInnerHTML 
          {:__html (str "<iframe src=\"" (:video_url post) "\" frameborder=\"0\" allow=\"autoplay; fullscreen; picture-in-picture\" allowfullscreen></iframe>")}}]
        
        (and (seq (:tags post)) (str/includes? (:tags post) "carousel"))
        ;; If Carousel Tag exists
        [carousel]

        :else
        ;; Else: Show Image (if exists) -> Clickable for Zoom
        (when (seq (:image_url post))
          [:img.modal-image {:src (:image_url post)
                             :on-click #(reset! zoomed-image (:image_url post))
                             :title "Click to zoom"}]))
      
      [:div.modal-text
       [:h2 (:title post)]
       [:div.post-meta [:span (:date post)]]
       
       ;; Formatted Body
       [:div.post-body 
        (format-body (:body post))]
       
       (when (seq (:link post))
         [:a.btn-dark {:href (:link post) :target "_blank" :style {:margin-top "20px" :display "inline-block"}} 
          "Visit Link →"])]]]))

(defn post-card [post]
  (let [tags (when (seq (:tags post))
               (map str/trim (str/split (:tags post) #",")))
        extra-classes (if tags
                        (str/join " " (map #(str "tag-" (str/replace % #"\s+" "-")) tags))
                        "")]
    [:div.post-card {:class extra-classes :on-click #(reset! active-post post)}
     ;; Always show image in card view
     (when (seq (:image_url post))
       [:img.post-image {:src (:image_url post)}])
     
     [:div.post-meta
      [:span (:date post)]
      (when (seq (:type post))
        [:span (str/upper-case (:type post))])]
     
     [:h3.post-title 
      (:title post)]
     
     ;; Dynamic limit based on image presence
     (let [has-image? (seq (:image_url post))
           char-limit (if has-image? 150 450)
           body-text (or (:body post) "")]
       [:div.post-body 
        (if (> (count body-text) char-limit)
          (str (subs body-text 0 char-limit) "...")
          body-text)])
     
     (when tags
       [:div.post-tags
        (doall
          (for [tag tags]
            [:span.tag {:key tag} tag]))])]))



(defn feed []
  (cond
    @loading [:div "Loading archives..."]
    @error-msg [:div.error @error-msg]
    :else
    [:div.feed-grid
     (doall
       (for [post @posts]
         ^{:key (or (:date post) (:title post))}
         [post-card post]))]))

(defn main-component []
  [:div.app-container
   [navbar]
   [feed]
   ;; Post Modal
   (when @active-post
     [modal @active-post])
   ;; Fullscreen Image Zoom
   (when @zoomed-image
     [fullscreen-image-view])])

;; -- Data Fetching --
(defn fetch-posts! []
  (reset! loading true)
  (.parse Papa "https://docs.google.com/spreadsheets/d/1GFGfvmCjorovLP4XdIzdFYxDpkHkNMoifw-U0xxmym8/export?format=csv"
          (clj->js {:download true
                    :header true
                    :complete (fn [results]
                                (let [data (js->clj (.-data results) :keywordize-keys true)
                                      valid-data (filter #(seq (:title %)) data)
                                      intro (first (filter #(= "Programmer, Creator, Human" (:title %)) valid-data))
                                      others (remove #(= "Programmer, Creator, Human" (:title %)) valid-data)
                                      sorted (sort-by :date > others)]
                                  
                                  (if intro
                                    (reset! posts (cons intro sorted))
                                    (reset! posts sorted))
                                    
                                  (reset! loading false)))
                    :error (fn [err]
                             (reset! error-msg "Failed to load CSV data.")
                             (reset! loading false))})))

;; -- Init --
(defn mount-root []
  (rdom/render [main-component] (.getElementById js/document "app")))

(defn init []
  (fetch-posts!)
  (mount-root))

(init)
