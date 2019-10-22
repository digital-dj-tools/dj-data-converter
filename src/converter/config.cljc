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

(s/fdef ::progress
  :args (s/cat :function any?))

(def config-spec
  (spec/with-gen-fmap-spec
    (std/spec
     {:name ::config
      :spec {:clock ::time/clock
             :input ::inputs
             :output ::outputs
             :progress ::progress}})
    #(if (= :traktor (:input %))
       (assoc % :output :rekordbox :progress identity)
       (assoc % :output :traktor :progress identity))))

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