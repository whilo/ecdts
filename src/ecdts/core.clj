(ns ecdts.core
  (:refer-clojure :exclude [conj!]))

(set! *warn-on-reflection* true)

(defprotocol PIncrementable
  "Implementation detail. Needed because we can only mutate (set!)
  inside of deftype for thread safety reasons."
  (-inc [this]))

(defprotocol PMergeable
  "Protocol to merge a thread-local version of a datatype into the global version"
  (-merge [global local]))

(deftype ECCounter [^:unsynchronized-mutable ^long a
                    ^:unsynchronized-mutable ^long b]
  clojure.lang.IDeref
  (deref [this] (+ a b))
  PIncrementable
  (-inc [this] (set! b (inc b)))
  PMergeable
  (-merge [this other]
    (locking this
      (locking other
        (let [other ^ECCounter other
              g (+ a (.-b other))]
          (set! a g)
          (set! (.-b other) 0)
          (set! (.-a other) g)
          g)))))

(defn counter
  "Creates an eventual consistent increment only counter. Optionally
  you can initialize a thread-local version with a value."
  ([] (counter 0))
  ([init-value]
   (ECCounter. 0 init-value)))

(defn inc!
  "Increment the counter. (in-place)"
  [^ECCounter counter]
  (-inc counter))

(defn merge!
  "Merge local state into global state and update local state to
  reflect global changes. (in-place)"
  [global local]
  (-merge global local))


(comment
  (def foo (ECCounter. 0 0))

  (def bar (ECCounter. 0 0))

  (-inc foo)
  (-inc bar)

  (-merge foo foo)
  (-merge foo bar)

  (-inc bar)

  (def g (counter))

  (let [st (.getTime (java.util.Date.))
        ta (Thread. (fn []
                      (let [a (counter)]
                        (loop [i 0
                               j 0]
                          (when (< i 20000000)
                            (inc! a)
                            (if (> j 1000)
                              (do
                                (merge! g a)
                                (recur (inc i) 1))
                              (recur (inc i) (inc j)))))
                        (merge! g a)
                        (println "Thread A"
                                 (- (.getTime (java.util.Date.))
                                    st)))))
        tb (Thread. (fn []
                      (let [a (counter)]
                        (loop [i 0
                               j 0]
                          (when (< i 20000000)
                            (inc! a)
                            (if (> j 1000)
                              (do
                                (merge! g a)
                                (recur (inc i) 1))
                              (recur (inc i) (inc j)))))
                        (merge! g a)
                        (println "Thread B"
                                 (- (.getTime (java.util.Date.))
                                    st)))))
        tc (Thread. (fn []
                      (let [a (counter)]
                        (loop [i 0
                               j 0]
                          (when (< i 20000000)
                            (inc! a)
                            (if (> j 1000)
                              (do
                                (merge! g a)
                                (recur (inc i) 1))
                              (recur (inc i) (inc j)))))
                        (merge! g a)
                        (println "Thread C"
                                 (- (.getTime (java.util.Date.))
                                    st)))))
        td (Thread. (fn []
                      (let [a (counter)]
                        (loop [i 0
                               j 0]
                          (when (< i 20000000)
                            (inc! a)
                            (if (> j 1000)
                              (do
                                (merge! g a)
                                (recur (inc i) 1))
                              (recur (inc i) (inc j)))))
                        (merge! g a)
                        (println "Thread D"
                                 (- (.getTime (java.util.Date.))
                                    st)))))]
    (.start ta)
    (.start tb)
    (.start tc)
    (.start td))

  @g

  (let [g (atom 0)
        st (.getTime (java.util.Date.))
        ta (Thread. (fn []
                      (loop [i 0]
                        (when (< i 20000000)
                          (swap! g inc)
                          (recur (inc i))))
                      (println "Thread A"
                               (- (.getTime (java.util.Date.))
                                  st))))
        tb (Thread. (fn []
                      (loop [i 0]
                        (when (< i 20000000)
                          (swap! g inc)
                          (recur (inc i))))
                      (println "Thread B"
                               (- (.getTime (java.util.Date.))
                                  st))))
        tc (Thread. (fn []
                      (loop [i 0]
                        (when (< i 20000000)
                          (swap! g inc)
                          (recur (inc i))))
                      (println "Thread C"
                               (- (.getTime (java.util.Date.))
                                  st))))
        td (Thread. (fn []
                      (loop [i 0]
                        (when (< i 20000000)
                          (swap! g inc)
                          (recur (inc i))))
                      (println "Thread D"
                               (- (.getTime (java.util.Date.))
                                  st))))]
    (.start ta)
    (.start tb)
    (.start tc)
    (.start td)))


(defprotocol PConjable
  "Implementation detail. Needed because we can only mutate (set!)
  inside of deftype for thread safety reasons."
  (-conj [this elem]))

(defn conj!
  "Adds element to an AddOnlyBag."
  [bag elem]
  (-conj bag elem))

(deftype ECAddOnlyBag [^:unsynchronized-mutable l
                       ^:unsynchronized-mutable appender]
  clojure.lang.IDeref
  (deref [this] (concat appender l))
  PMergeable
  (-merge [this other]
    (locking this
      (locking other
        (let [other ^ECAddOnlyBag other
              new-list (concat (.-appender other) l)]
          (set! l new-list)
          (set! (.-l other) new-list)
          (set! (.-appender other) '())
          new-list))))
  PConjable
  (-conj [this elem]
    (locking appender
      (set! appender (cons elem appender)))))

(defn add-only-bag
  "Creates an AddOnlyBag. Optionally you can initialize a
  thread-local version with a seq value."
  ([] (add-only-bag '()))
  ([init-values]
   (ECAddOnlyBag. '() init-values)))

(comment
  (def foo (ECAddOnlyBag. '() '(1 2 3)))

  (def bar (ECAddOnlyBag. '() '()))

  (-conj foo 4)

  (-merge bar foo)

  (def g (add-only-bag))

  (let [st (.getTime (java.util.Date.))
        ta (Thread. (fn []
                      (let [a (add-only-bag)]
                        (loop [i 0
                               j 0]
                          (when (< i 2000000)
                            (conj! a i)
                            (if (> j 1000)
                              (do
                                (merge! g a)
                                (recur (inc i) 1))
                              (recur (inc i) (inc j)))))
                        (merge! g a)
                        (println "Thread A"
                                 (- (.getTime (java.util.Date.))
                                    st)))))
        tb (Thread. (fn []
                      (let [a (add-only-bag)]
                        (loop [i 0
                               j 0]
                          (when (< i 2000000)
                            (conj! a i)
                            (if (> j 1000)
                              (do
                                (merge! g a)
                                (recur (inc i) 1))
                              (recur (inc i) (inc j)))))
                        (merge! g a)
                        (println "Thread B"
                                 (- (.getTime (java.util.Date.))
                                    st)))))
        tc (Thread. (fn []
                      (let [a (add-only-bag)]
                        (loop [i 0
                               j 0]
                          (when (< i 2000000)
                            (conj! a i)
                            (if (> j 1000)
                              (do
                                (merge! g a)
                                (recur (inc i) 1))
                              (recur (inc i) (inc j)))))
                        (merge! g a)
                        (println "Thread C"
                                 (- (.getTime (java.util.Date.))
                                    st)))))
        td (Thread. (fn []
                      (let [a (add-only-bag)]
                        (loop [i 0
                               j 0]
                          (when (< i 2000000)
                            (conj! a i)
                            (if (> j 1000)
                              (do
                                (merge! g a)
                                (recur (inc i) 1))
                              (recur (inc i) (inc j)))))
                        (merge! g a)
                        (println "Thread D"
                                 (- (.getTime (java.util.Date.))
                                    st)))))]
    (.start ta)
    (.start tb)
    (.start tc)
    (.start td))

  (count @g)

  (let [g (atom '())
        st (.getTime (java.util.Date.))
        ta (Thread. (fn []
                      (loop [i 0]
                        (when (< i 2000000)
                          (swap! g conj i)
                          (recur (inc i))))
                      (println "Thread A"
                               (- (.getTime (java.util.Date.))
                                  st))))
        tb (Thread. (fn []
                      (loop [i 0]
                        (when (< i 2000000)
                          (swap! g conj i)
                          (recur (inc i))))
                      (println "Thread B"
                               (- (.getTime (java.util.Date.))
                                  st))))
        tc (Thread. (fn []
                      (loop [i 0]
                        (when (< i 2000000)
                          (swap! g conj i)
                          (recur (inc i))))
                      (println "Thread C"
                               (- (.getTime (java.util.Date.))
                                  st))))
        td (Thread. (fn []
                      (loop [i 0]
                        (when (< i 2000000)
                          (swap! g conj i)
                          (recur (inc i))))
                      (println "Thread D"
                               (- (.getTime (java.util.Date.))
                                  st))))]
    (.start ta)
    (.start tb)
    (.start tc)
    (.start td)))
