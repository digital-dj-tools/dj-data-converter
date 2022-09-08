#?(:clj (set! *warn-on-reflection* true))
(ns converter.error
  (:require
   #?(:cljs [cljs-node-io.core :as io :refer [spit]])
   #?(:clj [clojure.pprint :as pprint] :cljs [cljs.pprint :as pprint])
   #?(:cljs [clojure.string :as string])))

#?(:cljs
   (defn Error->map
     [e]
     (let [cause (ex-cause e)
           type (type e)
           message (ex-message e)
           stack (.-stack e)
           trace (as-> stack $ (string/split $ #"\n") (mapv string/trim $))
           at (first trace)]
       (cond-> {}
         cause (assoc :cause cause)
         true (assoc :via [(cond->
                            {:type type}
                             message (assoc :message message)
                             (instance? ExceptionInfo e) (assoc :data (ex-data e))
                             stack (assoc :at at))])
         stack (assoc :trace trace)
         (instance? ExceptionInfo e) (assoc :data (ex-data e))))))

(defn data-error? [error]
  (contains? error :data))

(defn truncate-error
  [{{:keys [problems]} :data :as error}]
  (cond->
   (-> error
       (update-in [:via 0 :data] dissoc :value)
       (update-in [:data] dissoc :value))
    problems (-> (update-in [:via 0 :data :problems] #(take 1 %))
                 (update-in [:via 0 :data :problems] (fn [problem] (map #(dissoc % :val) problem)))
                 (update-in [:data :problems] #(take 1 %))
                 (update-in [:data :problems] (fn [problem] (map #(dissoc % :val) problem))))))

(defn create-report
  [error arguments options]
  {:args arguments
   :opts options
   :error (if (data-error? error)
            (truncate-error error)
            error)})

(defn write-report
  [report output-dir]
  (spit (str (if output-dir (str output-dir "/")) "error-report.edn")
        (with-out-str (pprint/pprint report))))