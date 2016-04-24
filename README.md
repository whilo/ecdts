# Eventually Consistent Datatypes

This is an implementation of highly-scalable [eventually consistent
datatypes](https://dl.acm.org/citation.cfm?id=2911158) for Clojure.
When you do massively parallel updates on STM, the abort rate can
become prohibitive and you don't want to wait until your transactions
succeed. One example is the counter from the paper, where each thread
increments the counter in some inner loop. And you asynchronously
merge the thread-local var from time to time with the global state.


While I haven't had a use case for them yet, I found the research
interesting and wanted to benchmark the approach against the Clojure
STM. I think the datatypes become interesting in many-core scenarios
where you have more than 10 (maybe 100+) threads hammering on a
datastructure.

I am not sure about the access semantics for the bag, it would be very
well possible to use something different than sequences there,
e.g. sets or vectors, since I haven't used Cons cells (Clojure doesn't
use them), but Clojure's persistent lists which also have constant
time concatenation due to lazyness.

You will still have `O(#elements)` worse case access time for other
collections though, but subsequent accesses could benefit from a
different datatype. I haven't implemented the OR-set from the paper
yet, as it is more complicated and I am not sure whether a binary tree
would perform well. Feel free to add it though :) or open an issue, if
you have a use-case.

## Usage

[![Clojars Project](http://clojars.org/es.topiq/ecdts/latest-version.svg)](http://clojars.org/es.topiq/ecdts)

Example benchmark for the counter. You always have a global version,
similar to a Clojure atom, which you merge into from time to time. The
datatypes are dereferable to retrieve the current thread-local
value. Read the paper for details.

~~~clojure
(require '[ecdts.core :refer :all])

(def g (counter))

;; example benchmark for the counter
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
~~~

The bag works similarly.

~~~clojure
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
~~~

## License

Copyright © 2016 Christian Weilbach

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
