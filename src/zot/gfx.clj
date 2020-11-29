(ns zot.gfx
  (:import [org.jetbrains.skija Canvas Paint Rect]))

(defn to-int [^long l]
  (.intValue (Long/valueOf l)))

(defn paint [{:keys [color]}]
  (doto (Paint.) (.setColor (to-int color))))
