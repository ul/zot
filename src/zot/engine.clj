(ns zot.engine
  (:import
   [com.jogamp.opengl GLAutoDrawable]
   [com.jogamp.newt.event MouseEvent KeyEvent])
  (:require
   [thi.ng.geom.gl.jogl.core :as jogl]))

(set! *warn-on-reflection* true)

(defn start
  "Create an OpenGL window and attach render/event callbacks in livecoding-friendly manner."
  [{:keys [init display resize dispose key-pressed mouse-pressed mouse-dragged wheel-moved]}]
  (let [window (atom nil)
        display+ (fn [^GLAutoDrawable drawable t]
                   (try
                     (display drawable t)
                     (catch Exception e (println (.getMessage e)))))
        resize+ (fn [_ x y w h]
                  (try
                    (resize x y w h)
                    (catch Exception e (println (.getMessage e)))))
        key-pressed+ (fn [^KeyEvent e]
                       (try
                         (key-pressed e)
                         (catch Exception e (println (.getMessage e)))))
        mouse-pressed+ (fn [^MouseEvent e]
                         (try
                           (mouse-pressed e)
                           (catch Exception e (println (.getMessage e)))))
        mouse-dragged+ (fn [^MouseEvent e]
                         (try
                           (mouse-dragged e)
                           (catch Exception e (println (.getMessage e)))))
        wheel-moved+ (fn [^MouseEvent e deltas]
                       (try
                         (wheel-moved e deltas)
                         (catch Exception e (println (.getMessage e)))))]
    (reset!
     window
     (jogl/gl-window
      {:profile       :gl3
       :samples       4
       :double-buffer true
       :fullscreen    false
       :events        {:init    init
                       :display display
                       :resize  resize+
                       :dispose dispose
                       :keys    {:press key-pressed+}
                       :mouse   {:press mouse-pressed+
                                 :drag  mouse-dragged+
                                 :wheel wheel-moved+}}}))))
