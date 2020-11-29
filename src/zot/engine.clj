(ns zot.engine
  (:import
   [org.jetbrains.skija
    BackendRenderTarget
    Canvas
    ColorSpace
    DirectContext
    FramebufferFormat
    Paint
    Rect
    Surface
    SurfaceColorFormat
    SurfaceOrigin]
   [org.lwjgl.glfw
    Callbacks
    GLFW
    GLFWErrorCallback
    GLFWKeyCallbackI
    GLFWCursorPosCallbackI
    GLFWMouseButtonCallbackI
    GLFWWindowSizeCallbackI]
   [org.lwjgl.opengl GL GL11]
   [org.lwjgl.system MemoryUtil]))

(defn color [^long l]
  (.intValue (Long/valueOf l)))

(defn start [{:keys [width height title
                     on-draw on-key on-cursor on-mouse-button
                     clear-color]
              :or {clear-color 0xFF000000}}]

  (.set (GLFWErrorCallback/createPrint System/err))

  (GLFW/glfwInit)
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)

  (let [width (atom width)
        height (atom height)
        dpi (atom [1.0 1.0])
        cursor-x (atom 0)
        cursor-y (atom 0)
        context (atom nil)
        surface (atom nil)
        target (atom nil)
        canvas (atom nil)
        os (.toLowerCase (java.lang.System/getProperty "os.name"))
        window (GLFW/glfwCreateWindow @width @height title MemoryUtil/NULL MemoryUtil/NULL)
        update-dimensions (fn []
                            (let [w (int-array 1)
                                  h (int-array 1)
                                  x-scale (float-array 1)
                                  y-scale (float-array 1)]
                              (GLFW/glfwGetFramebufferSize window w h)
                              (GLFW/glfwGetWindowContentScale window x-scale y-scale)
                              (reset! width (/ (aget w 0) (aget x-scale 0)))
                              (reset! height (/ (aget h 0) (aget y-scale 0)))
                              (reset! dpi [(aget x-scale 0) (aget y-scale 0)])))
        init-skia (fn []
                    (when-let [surface @surface]
                      (.close surface))
                    (when-let [target @target]
                      (.close target))
                    (reset! target
                            (BackendRenderTarget/makeGL
                             (* @width (get @dpi 0))
                             (* @height (get @dpi 1))
                             0 ;; samples
                             8 ;; stencil
                             (GL11/glGetInteger 0x8CA6) ;; GL_FRAMEBUFFER_BINDING
                             FramebufferFormat/GR_GL_RGBA8))
                    (reset! surface (Surface/makeFromBackendRenderTarget
                                     @context @target
                                     SurfaceOrigin/BOTTOM_LEFT
                                     SurfaceColorFormat/RGBA_8888
                                     (ColorSpace/getSRGB)))
                    (reset! canvas  (.getCanvas @surface))
                    (.scale @canvas (get @dpi 0) (get @dpi 1)))
        draw (fn []
               (let [canvas @canvas]
                 (.clear canvas (color clear-color))
                 (let [layer (.save canvas)]
                   (try (on-draw canvas {:width @width
                                         :height @height
                                         :cursor-x @cursor-x
                                         :cursor-y @cursor-y})
                        (catch Exception e (.getMessage e)))
                   (.restoreToCount canvas layer)))
               (.flush @context)
               (GLFW/glfwSwapBuffers window))]

    (GLFW/glfwSetKeyCallback
     window
     (reify GLFWKeyCallbackI
       (invoke [this window key scancode action mods]
         (when (and (= key GLFW/GLFW_KEY_ESCAPE) (= action GLFW/GLFW_RELEASE))
           (GLFW/glfwSetWindowShouldClose window true))
         (try (on-key {:key key
                       :scancode scancode
                       :action action
                       :mods mods})
              (catch Exception e (.getMessage e))))))

    (GLFW/glfwSetCursorPosCallback
     window
     (reify GLFWCursorPosCallbackI
       (invoke [this window x y]
         (let [mac (or (.contains os "mac")
                       (.contains os "darwin"))
               [dpi-x dpi-y] @dpi]
           (reset! cursor-x (if mac x (/ x dpi-x)))
           (reset! cursor-y (if mac y (/ y dpi-y)))
           (try (on-cursor @cursor-x @cursor-y)
                (catch Exception e (.getMessage e)))))))

    (GLFW/glfwSetMouseButtonCallback
     window
     (reify GLFWMouseButtonCallbackI
       (invoke [this window button action mods]
         (try (on-mouse-button {:button button
                                :action action
                                :mods mods})
              (catch Exception e (.getMessage e))))))

    (GLFW/glfwSetWindowSizeCallback
     window
     (reify GLFWWindowSizeCallbackI
       (invoke [this window new-width new-height]
         (update-dimensions)
         (init-skia)
         (draw))))

    (GLFW/glfwSetInputMode window GLFW/GLFW_CURSOR GLFW/GLFW_CURSOR_HIDDEN)

    (update-dimensions)

    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwSwapInterval 1) ;; v-sync
    (GLFW/glfwShowWindow window)

    (GL/createCapabilities)

    (reset! context (DirectContext/makeGL))
    (init-skia)
    (draw)

    (while (not (GLFW/glfwWindowShouldClose window))
      (draw)
      (GLFW/glfwPollEvents))

    (Callbacks/glfwFreeCallbacks window)
    (GLFW/glfwHideWindow window)
    (GLFW/glfwDestroyWindow window)
    (GLFW/glfwPollEvents)
    (GLFW/glfwTerminate)
    (.free (GLFW/glfwSetErrorCallback nil))))
