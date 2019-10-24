(ns converter.config
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :as string]
   [converter.progress :as progress]
   [converter.spec :as spec]
   [converter.time :as time]
   [spec-tools.data-spec :as std]
   [tick.alpha.api :as tick]))

(s/def ::inputs
  #{:traktor :rekordbox})

(s/def ::outputs
  #{:rekordbox :traktor})

(defn traktor->rekordbox?
  [{:keys [input output]}]
  (and (= input :traktor) (= output :rekordbox)))

(s/fdef ::progress
  :args (s/cat :function any?))

(def config-base-spec
  (std/spec
   {:name ::config-base
    :spec {:clock ::time/clock
           :input ::inputs
           :output ::outputs
           :progress ::progress}}))

(s/def ::config
  (s/with-gen (s/and config-base-spec #(not= (:input %) (:output %)))
    (fn [] (gen/fmap #(if (= :traktor (:input %))
                        (assoc % :output :rekordbox :progress identity)
                        (assoc % :output :traktor :progress identity))
                     (s/gen config-base-spec)))))

(defn arguments->config
  [{:keys [input-file] :as arguments}]
  (merge
   (cond
     (string/ends-with? input-file ".nml") {:input :traktor :output :rekordbox}
     (string/ends-with? input-file ".xml") {:input :rekordbox :output :traktor}
     :else (throw (ex-info "Could not determine input format for provided arguments" 
                           {:arguments arguments})))
   {:progress (partial progress/dots-println 1000)
    :clock (tick/clock)}))