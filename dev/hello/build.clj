(ns hello.build
  (:require [clojure.tools.build.api :as b]
            [clojure.java.io :as io]
            [cheshire.core :as json])
  (:import (java.lang ProcessBuilder$Redirect)
           (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers HttpResponse)
           (java.net URI)
           (java.nio.channels ClosedChannelException)))

(set! *warn-on-reflection* true)

(def lib 'pedestal-native/app)
(def class-dir "target/classes")
(def uber-file "target/pedestal-native.jar")

(defn ^ProcessBuilder pb-inherit
  [& vs]
  (doto (ProcessBuilder. ^"[Ljava.lang.String;" (into-array (map str vs)))
    (.redirectOutput ProcessBuilder$Redirect/INHERIT)
    (.redirectError ProcessBuilder$Redirect/INHERIT)))

(defn test-cmd
  [cmd]
  (let [^ProcessBuilder pb (apply pb-inherit cmd)
        p (.start pb)]
    (try
      (let [client (HttpClient/newHttpClient)
            _ (Thread/sleep 5000)
            ^HttpResponse response (loop [n 0]
                                     (when (< 10 n)
                                       (throw (ex-info "too much" {:n n})))
                                     (Thread/sleep 100)
                                     (let [result (try
                                                    (.send client
                                                           (.build (HttpRequest/newBuilder (URI/create "http://localhost:8080")))
                                                           (HttpResponse$BodyHandlers/ofString))
                                                    (catch ClosedChannelException ex
                                                      nil))]
                                       (if result
                                         result
                                         (recur (inc n)))))]
        (prn {:status (.statusCode response)}))
      (finally
        (.destroy p)
        (.waitFor p)
        (prn {:exit (.exitValue p)})))))

(defn -main
  [& _]
  (let [basis (b/create-basis {:project "deps.edn"})]
    (b/delete {:path "target"})
    (b/write-pom {:class-dir class-dir
                  :lib       lib
                  :version   "1.0.0"
                  :basis     basis
                  :src-dirs  (:paths basis)})
    (b/compile-clj {:basis     basis
                    :src-dirs  (:paths basis)
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :main      'hello.main
             :uber-file uber-file
             :basis     basis})
    (when-not (.exists (io/file (System/getProperty "java.home")
                                "bin" "native-image"))
      (-> (pb-inherit (io/file (System/getProperty "java.home")
                               "bin" "gu")
                      "install" "native-image")
          .start
          (doto (.waitFor))
          .exitValue
          prn))
    (.delete (io/file "." "pedestal-native"))
    (.delete (io/file "." "filter.json"))
    (.delete (io/file "." "reflect-config.json"))
    (.delete (io/file "." "proxy-config.json"))
    (.delete (io/file "." "resource-config.json"))
    (.delete (io/file "." "serialization-config.json"))
    (.delete (io/file "." "jni-config.json"))
    (spit (io/file "." "filter.json")
          (json/generate-string {}))
    (test-cmd [(io/file (System/getProperty "java.home")
                        "bin" "java")
               "-agentlib:native-image-agent=caller-filter-file=filter.json,config-output-dir=."
               "-jar"
               (io/file "target" "pedestal-native.jar")])
    (-> (pb-inherit
         (io/file (System/getProperty "java.home")
                  "bin" "native-image")
         "-jar" (io/file "target" "pedestal-native.jar")
         "-H:Name=pedestal-native"
         "-H:+ReportExceptionStackTraces"
         "--allow-incomplete-classpath"
         "--initialize-at-build-time"
         "--verbose"
         "-H:ReflectionConfigurationFiles=reflect-config.json"
         "--no-fallback")
        .start
        (doto (.waitFor))
        .exitValue
        prn)
    (test-cmd [(io/file "." "pedestal-native")])))

(comment
 (-main))


