(ns hello.main-test
  (:require [clojure.test :refer [deftest is]]
            [io.pedestal.test :refer [response-for]]
            [hello.main :as hello]
            [io.pedestal.http :as http]))

(deftest world
  (let [service-fn (-> {::http/routes (hello/expand-routes {})}
                       http/default-interceptors
                       http/create-servlet
                       ::http/service-fn)]
    (is (= 202
           (:status (response-for service-fn :get "/"))))))
