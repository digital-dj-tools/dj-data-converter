(set! *warn-on-reflection* true)
(ns converter.error
  (:require
   [clojure.pprint :as pprint]))

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
  (spit (str (if output-dir (str output-dir "/") "") "error-report.edn")
        (with-out-str (pprint/pprint report))))