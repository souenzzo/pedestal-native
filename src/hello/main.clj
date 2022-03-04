(ns hello.main
  (:gen-class)
  (:require [io.pedestal.http.route :as route]
            [io.pedestal.http.jetty]
            [io.pedestal.http :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import (org.eclipse.jetty.server Server)
           (java.util.jar Manifest)))

(def ci-commit-sha
  (System/getenv "CI_COMMIT_SHA"))

(def property-version
  (System/getProperty "hello.main.version"))

(defn expand-routes
  [_]
  (let [version (fn [_]
                  {:body   (-> {:env      ci-commit-sha
                                :property property-version
                                :manifest (some-> "META-INF/MANIFEST.MF"
                                                  io/resource
                                                  io/input-stream
                                                  Manifest.
                                                  (.getAttributes "SCM-Revision")
                                                  str)}
                               (json/generate-string {:pretty true}))
                   :status 200})]
    (route/expand-routes #{["/version" :get version
                            :route-name ::version]})))

(defonce *state (atom nil))
(defn -main
  [& _]
  (prn [Server io.pedestal.http.jetty/start])
  (swap! *state
         (fn [st]
           (some-> st http/stop)
           (-> {::http/type   :jetty
                ::http/port   8080
                ::http/join?  false
                ::http/routes (expand-routes {})}
               http/default-interceptors
               http/create-server
               http/start))))
