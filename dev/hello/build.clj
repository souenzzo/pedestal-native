(ns hello.build
  (:require [clojure.tools.build.api :as b]
            [clojure.java.io :as io]
            [cheshire.core :as json])
  (:import (java.lang ProcessBuilder$Redirect)
           (java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers HttpResponse)
           (java.net URI)
           (java.nio.channels ClosedChannelException)
           (java.io File)))

(set! *warn-on-reflection* true)

(def lib 'pedestal-native/app)
(def class-dir "target/classes")
(def uber-file "target/pedestal-native.jar")

(def ^File ^:dynamic *pwd* nil)

(defn ^ProcessBuilder pb-inherit
  [& vs]
  (cond-> (ProcessBuilder. ^"[Ljava.lang.String;" (into-array (map str vs)))
          :always (doto (.redirectOutput ProcessBuilder$Redirect/INHERIT)
                        (.redirectError ProcessBuilder$Redirect/INHERIT))
          *pwd* (doto (.directory *pwd*))))

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
                                                           (.build (HttpRequest/newBuilder (URI/create "http://localhost:8080/version")))
                                                           (HttpResponse$BodyHandlers/ofString))
                                                    (catch ClosedChannelException ex
                                                      nil))]
                                       (if result
                                         result
                                         (recur (inc n)))))]
        (prn {:status (.statusCode response)})
        (println (.body response)))
      (finally
        (.destroy p)
        (.waitFor p)
        (prn {:exit (.exitValue p)})))))

(defn -main
  [& _]
  (let [basis (b/create-basis {:project "deps.edn"
                               :aliases [#_:log-noop]})]
    (b/delete {:path "target"})
    (b/write-pom {:class-dir class-dir
                  :lib       lib
                  :version   "1.0.0"
                  :basis     basis
                  :src-dirs  (:paths basis)})
    (b/compile-clj {:basis     basis
                    :java-opts (concat
                                (when-let [sha (System/getenv "CI_COMMIT_SHA")]
                                  [(str "-Dhello.main.version=" sha)]))
                    :src-dirs  (:paths basis)
                    :class-dir class-dir})
    (b/uber {:class-dir class-dir
             :main      'hello.main
             :uber-file uber-file
             :manifest  (merge {}
                               (when-let [sha (System/getenv "CI_COMMIT_SHA")]
                                 {"SCM-Revision" sha}))
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
    (let [target (io/file "target" "native")]
      (.mkdirs target)
      (binding [*pwd* target]
        (spit (io/file target "filter.json")
              (json/generate-string {}))
        (test-cmd [(io/file (System/getProperty "java.home")
                            "bin" "java")
                   "-agentlib:native-image-agent=caller-filter-file=filter.json,config-output-dir=."
                   "-jar"
                   (io/file ".." "pedestal-native.jar")])
        (-> (pb-inherit
             (io/file (System/getProperty "java.home")
                      "bin" "native-image")
             "-jar" (io/file ".." "pedestal-native.jar")
             "-H:Name=pedestal-native"
             "-H:+ReportExceptionStackTraces"
             "--allow-incomplete-classpath"
             "--initialize-at-build-time"
             "--verbose"
             "-H:DashboardDump=report/pedestal-native"
             "-H:+DashboardHeap"
             "-H:+DashboardCode"
             "-H:+DashboardBgv"
             "-H:+DashboardJson"
             "-H:ReflectionConfigurationFiles=reflect-config.json"
             "-H:ResourceConfigurationFiles=resource-config.json"
             "--no-fallback")
            .start
            (doto (.waitFor))
            .exitValue
            prn)
        (test-cmd [(io/file "./pedestal-native")])))))

(comment
 (-main))


