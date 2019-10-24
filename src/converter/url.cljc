(ns converter.url
  (:require [cemerick.url :refer [url url-encode]]
            #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
            #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
            [clojure.string :as string]
            [converter.str :as str]
            [spec-tools.core :as st]))

; it is assumed that the string is already url-encoded
(defn string->url
  [_ str]
  (if (string? str)
    (try
      ; on cljs/node, cemerick/url seems to be doing url decoding for whitespace (%20), no idea why..
      ; so on cljs, whitespace is url encoded again
      #?(:clj (url str)
         :cljs (-> str url (update :path #(string/replace % " " "%20"))))
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