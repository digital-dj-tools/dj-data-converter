(ns converter.config
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :as string]
   [converter.spec :as spec]
   [converter.time :as time]
   [spec-tools.data-spec :as std]
   [tick.alpha.api :as tick]))

(s/def ::inputs
  #{:traktor :rekordbox})

(s/def ::outputs
  #{:rekordbox :traktor})

(def config-spec
  (spec/with-gen-fmap-spec 
    (std/spec
     {:name ::config
      :spec {:input ::inputs
             :output ::outputs
             :clock ::time/clock}})
    #(if (= :traktor (:input %)) 
       (assoc % :output :rekordbox)
       (assoc % :output :traktor))))

(defn print-progress
  [f]
  (let [item-count (atom 1)]
    (fn [item]
      (when (= 0 (mod @item-count 1000))
        (println ".")
        #?(:clj (flush)))
      (swap! item-count inc)
      (f item))))

(defn arguments->config
  [{:keys [input-file] :as arguments}]
  (merge
   (cond
     (string/ends-with? input-file ".nml") {:input :traktor :output :rekordbox}
     (string/ends-with? input-file ".xml") {:input :rekordbox :output :traktor}
     :else (throw (ex-info "Could not determine config for provided arguments" {:arguments arguments})))
   {:progress print-progress
    :clock (tick/clock)}))