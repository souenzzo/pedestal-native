(ns hello.main-test
  (:require [clojure.test :refer [deftest is]]
            [io.pedestal.test :refer [response-for]]
            [hello.main :as hello]
            [io.pedestal.http :as http]
            [cheshire.core :as json]))

(deftest world
  (let [service-fn (-> {::http/routes (hello/expand-routes {})}
                       http/default-interceptors
                       http/create-servlet
                       ::http/service-fn)]
    (is (= {:env      nil
            :manifest nil
            :property nil}
           (json/parse-string (:body (response-for service-fn :get "/version"))
                              true)))))
