(ns zot.main
  (:import
   [com.jogamp.opengl GL3 GLAutoDrawable]
   [com.jogamp.newt.event MouseEvent KeyEvent])
  (:require
   zot.engine
   [zot.ship :as ship]
   [thi.ng.color.core :as color]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.gl.core :as gl]
   [thi.ng.geom.gl.arcball :as arc]
   [thi.ng.geom.gl.jogl.core :as jogl]
   [thi.ng.geom.gl.jogl.constants :as glc]
   nrepl.server))

(def app (atom {;; Draw polygons with lines rather than fill them. Press 'w' to toggle.
                :wireframe true
                ;; All the data returned by the zot.engine/start.
                :engine nil
                ;; Cache for models and shaders. Set it to nil to invalidate. Press `r` to invalidate.
                ;; When invalidated, will be rebuilt next frame.
                ;; Apparently some of OpenGL calls fail if no issued within init/display callbacks
                ;; even when we pass a window as a drawable.
                ;; Leaks memory at the moment but negligible if invoked manually
                ;; (don't rebuild on each frame!).
                ;; This feature is exclusively for a hot code reload,
                ;; don't abuse it for a dynamism in the game.
                :cache nil
                :arcball (arc/arcball {})
                :blueprints {:ship (ship/make-ship)}
                :ships [{:position [0.0 0.0 0.0]}]
                :background-color (color/rgba 0.3 0.3 0.3)}))

(defn init [^GLAutoDrawable _drawable])

(defn make-cache [gl {:keys [ship]}]
  {:ship (ship/build-models gl ship)})

(defn display
  [^GLAutoDrawable drawable t]
  (let [^GL3 gl (.. drawable getGL getGL3)
        {:keys [arcball
                background-color
                blueprints
                cache
                ship-proj
                ships
                wireframe]} @app
        view (arc/get-view arcball)
        cache (or cache (make-cache gl blueprints))]
    (swap! app assoc :cache cache)
    (doto gl
      (gl/clear-color-and-depth-buffer background-color 1.0)
      (.glPolygonMode glc/front-and-back (if wireframe glc/line glc/fill)))
    (doseq [ship ships]
      (doseq [model (get cache :ship)]
        (gl/draw-with-shader
         gl
         (-> model
             (update :uniforms assoc
                     :time t
                     :model (g/translate mat/M44 (get ship :position))
                     :view view
                     :proj ship-proj)))))))

(defn resize
  [_x _y w h]
  (swap! app assoc :ship-proj (mat/perspective 90 (/ w h) 0.1 10))
  (swap! app update :arcball arc/resize w h))

(defn key-pressed
  [^KeyEvent e]
  (condp = (.getKeyCode e)
    KeyEvent/VK_ESCAPE (jogl/destroy-window (get-in @app [:engine :window]))
    (case (.getKeyChar e)
      \r (swap! app assoc :cache nil)
      \w (swap! app update :wireframe not)
      nil)))

(defn mouse-pressed [^MouseEvent e] (swap! app update :arcball arc/down (.getX e) (.getY e)))

(defn mouse-dragged [^MouseEvent e] (swap! app update :arcball arc/drag (.getX e) (.getY e)))

(defn wheel-moved [^MouseEvent _e deltas] (swap! app update :arcball arc/zoom-delta (* 10 (nth deltas 1))))

(defn dispose [_] (jogl/stop-animator (get-in @app [:engine :anim])))

(defn -main
  [& _args]
  (nrepl.server/start-server :port 7888)
  (swap! app assoc :engine
         (zot.engine/start {:init init
                            :display #'display
                            :resize #'resize
                            :dispose dispose
                            :key-pressed #'key-pressed
                            :mouse-pressed #'mouse-pressed
                            :mouse-dragged #'mouse-dragged
                            :wheel-moved #'wheel-moved}))
  nil)

(comment
  (-main)
  (do (swap! app assoc-in [:ships 0 :position] [0.0 0.0 0.0]) nil))
