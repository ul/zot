(ns zot.main
  (:require [nrepl.server :as nrepl]
            zot.engine
            [zot.gfx :as gfx])
  (:import [org.jetbrains.skija Canvas Paint Rect]))

;; Writing a game in Clojure boy? Gotta care about perf.
(set! *warn-on-reflection* true)

(def world (atom {:rotating-rect-color 0xFFCC3333}))

(defn on-draw [^Canvas canvas
               {:keys [width height cursor-x cursor-y]}]
  (.save canvas)
  (.translate canvas (/ width 2) (/ height 2))
  (.rotate canvas (mod (/ (System/currentTimeMillis) 10) 360))
  (.drawRect canvas (Rect/makeXYWH -50 -50 100 100) (gfx/paint {:color (:rotating-rect-color @world)}))
  (.restore canvas)
  (.translate canvas cursor-x cursor-y)
  (.drawRect canvas (Rect/makeXYWH -5 -5 10 10) (gfx/paint {:color 0xFFFFFFFF})))

(defn on-key [{:keys [key scancode action mods]}]
  (println "Some key was the most definitely actioned, can't tell more at the moment"))

(defn on-cursor [x y])
(defn on-mouse-button [{:keys [button action mods]}])

(defn -main [& args]
  (.start (Thread. #(clojure.main/main)))

  (nrepl/start-server :port 7888)
  (println "nREPL server started at locahost:7888")

  (zot.engine/start
   {:width 640
    :height 480
    :title "Zones of Thought: the Game"
    :on-key #'on-key
    :on-draw #'on-draw
    :on-cursor #'on-cursor
    :on-mouse-button #'on-mouse-button}))
