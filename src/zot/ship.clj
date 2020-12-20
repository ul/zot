(ns zot.ship
  (:require
   [thi.ng.geom.core :as g]
   [thi.ng.geom.cuboid :as cuboid]
   [thi.ng.geom.matrix :as mat]
   [thi.ng.geom.gl.core :as gl]
   [thi.ng.geom.gl.glmesh :as glm]
   [thi.ng.geom.gl.jogl.constants :as glc]))

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

(defn make-ship
  "Build a ship as a vector of primitive models."
  []
  (mapv make-model model-coords))
