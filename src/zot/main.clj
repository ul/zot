(ns zot.main
  (:import
   [com.jogamp.opengl GL3 GLAutoDrawable]
   [com.jogamp.newt.event MouseEvent KeyEvent])
  (:require
   [zot.engine]
   [thi.ng.color.core :as col]
   [thi.ng.geom.core :as g]
   [thi.ng.geom.aabb :as a]
   [thi.ng.geom.cuboid :as cuboid]
   [thi.ng.geom.attribs :as attr]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.gl.core :as gl]
   [thi.ng.geom.gl.arcball :as arc]
   [thi.ng.geom.gl.shaders :as sh]
   [thi.ng.geom.gl.shaders.lambert :as lambert]
   [thi.ng.geom.gl.glmesh :as glm]
   [thi.ng.geom.gl.jogl.core :as jogl]
   [thi.ng.geom.gl.jogl.constants :as glc]))

(def app (atom nil))

(def shader
  {:vs "
  void main() {
    vCol = vec4(position.xy * 0.5 + 0.5, fract(time), 1.0);
    vUV = uv;
    gl_Position = proj * view * model * vec4(position, 1.0);
  }"
   :fs "out vec4 fragColor;
  void main() {
    fragColor = vCol * texture(tex, vUV);
  }"
   :version  330
   :attribs  {:position :vec3
              :uv       :vec2}
   :varying  {:vCol     :vec4
              :vUV      :vec2}
   :uniforms {:model [:mat4 mat/M44]
              :view  :mat4
              :proj  :mat4
              :tex   [:sampler2D 0]
              :time  :float}
   :state    {:depth-test false
              :blend      true
              :blend-fn   [glc/src-alpha glc/one]}})

(defn init
  [^GLAutoDrawable drawable]
  (let [^GL3 gl (.. drawable getGL getGL3)
        shader (sh/make-shader-from-spec gl lambert/shader-spec-attrib 330)
        model   (-> (cuboid/cuboid [0 0 0] [1 0 0] [1 1 0] [0 1 0] [0 0 2] [1 0 2] [1 1 2] [0 1 2])
                    (g/center)
                    (g/as-mesh
                     {:mesh    (glm/indexed-gl-mesh 12 #{:col :fnorm})
                      :attribs {:col (->> [[1 0 0] [0 1 0] [0 0 1] [0 1 1] [1 0 1] [1 1 0]]
                                          (map col/rgba)
                                          (attr/const-face-attribs))}})
                    (gl/as-gl-buffer-spec {})
                    (assoc :shader shader))]
    (swap! app assoc :model model :arcball (arc/arcball {}) :shader shader)))

(defn display
  [^GLAutoDrawable drawable _t]
  (let [{:keys [model arcball]} @app
        ^GL3 gl (.. drawable getGL getGL3)]
    (doto gl
      (gl/clear-color-and-depth-buffer 0.3 0.3 0.3 1.0 1.0)
      (gl/draw-with-shader
       (update (gl/make-buffers-in-spec model gl glc/static-draw) :uniforms assoc
               :view (arc/get-view arcball))))))

(defn dispose [_] (jogl/stop-animator (:anim @app)))

(defn resize
  [_x _y w h]
  (swap! app assoc-in [:model :uniforms :proj] (mat/perspective 45 (/ w h) 0.1 10))
  (swap! app update :arcball arc/resize w h))

(defn reconfigure []
  (let [shader (:shader @app)
        model (-> (a/aabb 1)
                  (g/center)
                  (g/as-mesh
                   {:mesh    (glm/indexed-gl-mesh 12 #{:col :fnorm})
                    :attribs {:col (->> [[1 0 0] [0 1 0] [0 0 1] [0 1 1] [1 0 1] [1 1 0]]
                                        (map col/rgba)
                                        (attr/const-face-attribs))}})
                  (gl/as-gl-buffer-spec {})
                  (assoc :shader shader))]
    (swap! app assoc :model model)))

(defn key-pressed
  [^KeyEvent e]
  (condp = (.getKeyCode e)
    KeyEvent/VK_ESCAPE (jogl/destroy-window (:window @app))
    (case (.getKeyChar e)
      \r (reconfigure)
      nil)))

(defn mouse-pressed [^MouseEvent e] (swap! app update :arcball arc/down (.getX e) (.getY e)))

(defn mouse-dragged [^MouseEvent e] (swap! app update :arcball arc/drag (.getX e) (.getY e)))

(defn wheel-moved [^MouseEvent _e deltas] (swap! app update :arcball arc/zoom-delta (nth deltas 1)))

(defn -main
  [& _args]
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
