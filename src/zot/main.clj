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
   [thi.ng.geom.gl.jogl.constants :as glc]
   nrepl.server))

(def app (atom nil))

(def shader
  {:vs "
  void main() {
    vCol = vec4(position.xy * 0.5 + 0.5, fract(time), alpha);
    gl_Position = proj * view * model * vec4(position, 1.0);
  }"
   :fs "out vec4 fragColor;
  void main() {
    fragColor = vCol;
  }"
   :version  330
   :attribs  {:position :vec3
              :normal [:vec3 1]
              :color [:vec4 2]}
   :varying  {:vCol     :vec4}
   :uniforms {:model [:mat4 mat/M44]
              :view  :mat4
              :proj  :mat4
              :time  :float
              :normalMat  [:mat4 (gl/auto-normal-matrix :model :view)]
              :ambientCol [:vec3 [0 0 0]]
              :diffuseCol [:vec3 [1 1 1]]
              :lightCol   [:vec3 [1 1 1]]
              :lightDir   [:vec3 [0 0 1]]
              :alpha      [:float 1]}
   :state    {:depth-test true
              :blend      true
              :blend-fn   [glc/src-alpha glc/one]}})

(defn init
  [^GLAutoDrawable drawable]
  (let [^GL3 gl (.. drawable getGL getGL3)
        model   (-> (cuboid/cuboid [0 0 0] [1 0 0] [1 1 0] [0 1 0] [0.2 0.2 1] [0.8 0.2 1] [0.8 0.8 1] [0.2 0.8 1])
                    (g/center)
                    (g/as-mesh
                     {:mesh    (glm/indexed-gl-mesh 12 #{:col :fnorm})
                      :attribs {:col (->> [[1 0 0] [0 1 0] [0 0 1] [0 1 1] [1 0 1] [1 1 0]]
                                          (map col/rgba)
                                          (attr/const-face-attribs))}})
                    (gl/as-gl-buffer-spec {})
                )]
    (swap! app assoc :model model :arcball (arc/arcball {}) :rebuildshader true)))

(defn display
  [^GLAutoDrawable drawable t]
  (let [{:keys [model arcball rebuildshader cubeshader]} @app
        ^GL3 gl (.. drawable getGL getGL3)
       shader (if rebuildshader (sh/make-shader-from-spec gl shader 330) cubeshader)]
    (swap! app assoc :cubeshader shader :rebuildshader false)
    (doto gl
      (gl/clear-color-and-depth-buffer 0.3 0.3 0.3 1.0 1.0)
      (gl/draw-with-shader
       (update (gl/make-buffers-in-spec (assoc model :shader shader) gl glc/static-draw) :uniforms assoc
               :view (arc/get-view arcball) 
               :time t
               :alpha 1)))))

(defn dispose [_] (jogl/stop-animator (:anim @app)))

(defn resize
  [_x _y w h]
  (swap! app assoc-in [:model :uniforms :proj] (mat/perspective 45 (/ w h) 0.1 10))
  (swap! app update :arcball arc/resize w h))

(defn key-pressed
  [^KeyEvent e]
  (condp = (.getKeyCode e)
    KeyEvent/VK_ESCAPE (jogl/destroy-window (:window @app))
    (case (.getKeyChar e)
      \r (swap! app assoc :rebuildshader true)
      nil)))

(defn mouse-pressed [^MouseEvent e] (swap! app update :arcball arc/down (.getX e) (.getY e)))

(defn mouse-dragged [^MouseEvent e] (swap! app update :arcball arc/drag (.getX e) (.getY e)))

(defn wheel-moved [^MouseEvent _e deltas] (swap! app update :arcball arc/zoom-delta (* 10 (nth deltas 1))))

(defn -main
  [& _args]
  (nrepl.server/start-server :port 7888)
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
