(ns zot.main
  (:import
   [com.jogamp.opengl GL3 GLAutoDrawable]
   [com.jogamp.newt.event MouseEvent KeyEvent])
  (:require
   [zot.engine]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.cuboid :as cuboid]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.gl.core :as gl]
   [thi.ng.geom.gl.arcball :as arc]
   [thi.ng.geom.gl.shaders :as sh]
   [thi.ng.geom.gl.glmesh :as glm]
   [thi.ng.geom.gl.jogl.core :as jogl]
   [thi.ng.geom.gl.jogl.constants :as glc]
   nrepl.server))

(def app (atom nil))

(def shader
  {:vs "
  void main() {
    vCol = vec4(position.xy * 0.5 + 0.5, fract(time), 0.1+0.9*abs(sin(time)*cos(time)));
    gl_Position = proj * view * model * vec4(position, 1.0);
  }"
   :fs "out vec4 fragColor;
  void main() {
    fragColor = vCol;
  }"
   :version  330
   :attribs  {:position :vec3}
   :varying  {:vCol     :vec4}
   :uniforms {:model [:mat4 mat/M44]
              :view  :mat4
              :proj  :mat4
              :time  :float}
   :state    {:depth-test false
              :blend      true
              :blend-fn   [glc/src-alpha glc/one]}})

(defn x-axis-switch-cuboid [array]
  [(mapv * [-1 1 1] (nth array 3)) (mapv * [-1 1 1] (nth array 2)) (mapv * [-1 1 1] (nth array 1)) (mapv * [-1 1 1] (nth array 0))
   (mapv * [-1 1 1] (nth array 7)) (mapv * [-1 1 1] (nth array 6)) (mapv * [-1 1 1] (nth array 5)) (mapv * [-1 1 1] (nth array 4))])

(def model-coords
  [{:coords [[0.4 -0.4 0] [-0.4 -0.4 0] [-0.4 0.4 0] [0.4 0.4 0] [0.4 -0.4 7] [-0.4 -0.4 7] [-0.4 0.4 7] [0.4 0.4 7]] :trans [0 0 0] :sides "nsewfb"}
   {:coords [[-2 -0.2 3] [-2 -0.2 5] [-0.4 -0.2 5] [-0.4 -0.2 0] [-2 0.2 3] [-2 0.2 5] [-0.4 0.2 5] [-0.4 0.2 0]] :trans [-1.2 -0.2 -0.25] :sides "nsewfb"}
   {:coords (x-axis-switch-cuboid [[-2 -0.2 3] [-2 -0.2 5] [-0.4 -0.2 5] [-0.4 -0.2 0] [-2 0.2 3] [-2 0.2 5] [-0.4 0.2 5] [-0.4 0.2 0]]) :trans [1.2 -0.2 -0.25] :sides "nsewfb"}
   {:coords [[-2 -0.2 0] [-4 -0.2 2] [-0.4 -0.2 2] [-0.4 -0.2 0] [-2 0.2 0] [-4 0.2 2] [-0.4 0.2 2] [-0.4 0.2 0]] :trans [-1.7 -0.2 2.5] :sides "nsewfb"}
   {:coords (x-axis-switch-cuboid [[-2 -0.2 0] [-4 -0.2 2] [-0.4 -0.2 2] [-0.4 -0.2 0] [-2 0.2 0] [-4 0.2 2] [-0.4 0.2 2] [-0.4 0.2 0]]) :trans [1.7 -0.2 2.5] :sides "nsewfb"}
   {:coords [[0.1 -0.1 0] [-0.1 -0.1 0] [-0.1 0.1 0] [0.1 0.1 0] [0.1 -0.1 3] [-0.1 -0.1 3] [-0.1 0.1 3] [0.1 0.1 3]] :trans [-1.7 -0.2 -1] :sides "nsewfb"}
   {:coords [[0.1 -0.1 0] [-0.1 -0.1 0] [-0.1 0.1 0] [0.1 0.1 0] [0.1 -0.1 3] [-0.1 -0.1 3] [-0.1 0.1 3] [0.1 0.1 3]] :trans [1.7 -0.2 -1] :sides "nsewfb"}])

(defn make-model [{:keys [coords trans sides]}]
  (-> (apply cuboid/cuboid coords)
      (g/center)
      (g/translate trans)
      (g/as-mesh
       {:mesh    (glm/indexed-gl-mesh 12 #{})
        :flags sides})
      (gl/as-gl-buffer-spec {})))

(defn init
  [^GLAutoDrawable _drawable]
  (swap! app assoc
         :models (mapv make-model model-coords)
         :arcball (arc/arcball {})
         :re-build true
         :cache {}))

(defn display
  [^GLAutoDrawable drawable t]
  (let [{:keys [models arcball re-build cache wireframe]} @app
        ^GL3 gl (.. drawable getGL getGL3)
        view (arc/get-view arcball)
        cache
        (if re-build
          (let [shader (sh/make-shader-from-spec gl shader)]
            {:models
             (mapv (fn [model]
                     (gl/make-buffers-in-spec (assoc model :shader shader) gl glc/static-draw))
                   models)})
          cache)]
    (swap! app assoc :cache cache :re-build false)
    (doto gl
      (gl/clear-color-and-depth-buffer 0.3 0.3 0.3 1.0 1.0)
      (.glPolygonMode glc/front-and-back (if wireframe glc/line glc/fill)))
    (doseq [model (:models cache)]
      (gl/draw-with-shader
       gl
       (update model :uniforms assoc :view view :time t)))))

(defn dispose [_] (jogl/stop-animator (:anim @app)))

(defn resize
  [_x _y w h]
  (swap! app
         update :models
         (fn [models] (mapv (fn [model]
                              (assoc-in model [:uniforms :proj] (mat/perspective 90 (/ w h) 0.1 10)))
                            models)))
  (swap! app update :arcball arc/resize w h))

(defn key-pressed
  [^KeyEvent e]
  (condp = (.getKeyCode e)
    KeyEvent/VK_ESCAPE (jogl/destroy-window (:window @app))
    (case (.getKeyChar e)
      \r (swap! app assoc :re-build true)
      \w (swap! app update :wireframe not)
      nil)))

(defn mouse-pressed [^MouseEvent e] (swap! app update :arcball arc/down (.getX e) (.getY e)))

(defn mouse-dragged [^MouseEvent e] (swap! app update :arcball arc/drag (.getX e) (.getY e)))

(defn wheel-moved [^MouseEvent _e deltas] (swap! app update :arcball arc/zoom-delta (* 10 (nth deltas 1))))

(defn -main
  [& _args]
  ;; (nrepl.server/start-server :port 7888)
  (reset! app
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
