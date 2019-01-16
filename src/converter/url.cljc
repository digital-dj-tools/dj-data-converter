(ns converter.url
  (:require [cemerick.url :refer [url url-encode]]
            #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
            #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
            [converter.str :as str]
            [spec-tools.core :as st]))

(defn string->url
  [_ str]
  (if (string? str)
    (try
      (url str)
      (catch #?(:clj Exception, :cljs js/Error) _ str))
    str))

(defn url-gen
  []
  (->> (gen/tuple
      ;; base
        (gen/elements #{"file://localhost"})
      ;; drive letter (optional)
        (gen/one-of [(str/drive-letter-gen) (gen/elements #{""})])
      ;; path
        (->> (str/not-blank-string-with-whitespace-gen)
             (gen/fmap url-encode)
             (gen/vector)
             (gen/not-empty)))
       (gen/fmap
        #(apply url (flatten %)))))

(s/def ::url (st/spec (s/and
                       #(instance? cemerick.url.URL %)
                       #(= "file" (:protocol %)))
                      {:type :url
                       :gen #(url-gen)}))