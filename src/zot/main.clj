(ns zot.main
  (:import
   [com.jogamp.opengl GL3 GLAutoDrawable]
   [com.jogamp.newt.event MouseEvent KeyEvent])
  (:require
   zot.engine
   [zot.ship :as ship]
   [thi.ng.color.core :as color]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.gl.core :as gl]
   [thi.ng.geom.gl.arcball :as arc]
   [thi.ng.geom.gl.shaders :as sh]
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
                :cache nil
                :arcball (arc/arcball {})
                :ships [(ship/make-ship)]
                :background-color (color/rgba 0.3 0.3 0.3)}))

(defn make-cache [gl ships]
  (let [shader (sh/make-shader-from-spec gl ship/shader)
        build-model (fn [model]
                      (gl/make-buffers-in-spec (assoc model :shader shader) gl glc/static-draw))]
    {:models (mapcat (fn [ship] (mapv build-model ship)) ships)}))

(defn init [^GLAutoDrawable _drawable])

(defn display
  [^GLAutoDrawable drawable t]
  (let [^GL3 gl (.. drawable getGL getGL3)
        {:keys [arcball
                background-color
                cache
                ships
                wireframe]} @app
        view (arc/get-view arcball)
        cache (or cache (make-cache gl ships))]
    (swap! app assoc :cache cache)
    (doto gl
      (gl/clear-color-and-depth-buffer background-color 1.0)
      (.glPolygonMode glc/front-and-back (if wireframe glc/line glc/fill)))
    (doseq [model (:models cache)]
      (gl/draw-with-shader
       gl
       (update model :uniforms assoc :view view :time t)))))

(defn resize
  [_x _y w h]
  (let [update-model #(assoc-in % [:uniforms :proj] (mat/perspective 90 (/ w h) 0.1 10))
        update-models #(mapv update-model %)
        update-ships #(mapv update-models %)]
    (swap! app update :ships update-ships)
    (when (get @app :cache)
      (swap! app update-in [:cache :models] update-models)))
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

(comment (-main))
