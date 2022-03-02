(ns hello.build
  (:require [clojure.tools.build.api :as b]))


(def lib 'pedestal-native/app)
(def class-dir "target/classes")
(def uber-file "target/pedestal-native.jar")

(defn -main
  [& _]
  (let [basis (b/create-basis {:project "deps.edn"})]
    (b/delete {:path "target"})
    (b/write-pom {:class-dir class-dir
                  :lib       lib
                  :version   "1.0.0"
                  :basis     basis
                  :src-dirs  (:paths basis)})
    #_(b/copy-dir {:src-dirs   (:paths basis)
                   :target-dir class-dir})
    (b/compile-clj {:basis     basis
                    :src-dirs  (:paths basis)
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :main      'hello.main
             :uber-file uber-file
             :basis     basis})))
