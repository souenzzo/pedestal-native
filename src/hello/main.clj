(ns hello.main
  (:require [io.pedestal.http.route :as route]
            [io.pedestal.http :as http]))


(defn expand-routes
  [_]
  (route/expand-routes #{["/" :get (fn [_]
                                     {:status 202})
                          :route-name :hello]}))

(defonce *state (atom nil))
(defn -main
  [& _]
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
