(ns get-started.core 
  (:require [clojure.data.json :as json]))

(require '[clojure.data.json :as json])

(def json-data
  (-> "object_info.json"
      slurp
      (json/read-str :key-fn keyword)))

((count (map type (keys json-data))))

(map (fn [x] (:input (x json-data))) (take 1 (keys json-data)))

;; (defrecord Node [name input])
;; (defrecord Output [type])


(defn get-first-n-nodes [n] (map #(get json-data %) (take n (keys json-data))))
(def first-10-nodes (get-first-n-nodes 10))

(defn get-input-type [input]
  (let [input-type (first (last input))]
    (if (coll? input-type)
      "ONE_OF"
      input-type)))

(defn get-inputs [node]
  (let [{:keys [required optional]} (get node :input)]
    (merge required optional)))

(defn get-outputs [node]
  (let [stuff (get node :output)]
    stuff))

(defn is-node [] true)

;; (defn get-input-validator [input]
;;   (let [input-type (get-input-type input)]
;;     (case input-type
;;       "FLOAT" (fn [x] (and (number? x) (pos? x)))
;;       "INT" (fn [x] (and (integer? x) (pos? x)))
;;       "STRING" (fn [x] (and (string? x) (pos? x)))
;;       "LIST" (fn [x] (and (coll? x) (every? (fn [y] (pos? y)) x)))
;;       "ONE_OF" (fn [x] (some #(= x %) input))
;;       "CLIP" is-node
;;       "IMAGE" is-node
;;       "MASK" is-node)))


;; (defn filter-hidden-inputs [inputs] (filter #(not= (get-input-type %) "HIDDEN") inputs))

;; (->> first-10-nodes
;;      (map get-outputs))

;; (->> first-10-nodes
;;      (map get-inputs))

;; (defmacro define-node [node]
;;   `(defn ~(symbol (str "node-" (:name node))) [inputs]
;;      {:name ~(:name node)}))


;; (def not-colon #(not= % \:))
;; (replace {\: \a})

;; (def santitize-name #(apply str (replace {\space \_} (filter not-colon %))))

;; (->> (keys json-data)
;;      (map str)
;;      (map santitize-name))
(defn node-keyword [name] (keyword  name))

(defn unique-node-name [node-type node-inputs]
  (str node-type (hash (list node-type node-inputs))))

;; (unique-node-name "dick" (list {:dog \3}))

(defn node [node-type node-inputs]
  (->> (:output ((node-keyword node-type) json-data))
       (map-indexed (fn [index type] (hash-map type {:output-index index :node-type node-type :node-id (unique-node-name node-type node-inputs) :inputs node-inputs})))
       (reduce merge)))

(defn output-node [type node-inputs]
  {:node-type type :node-id (unique-node-name type 0) :inputs node-inputs})


(def basic-workflow
  (let [{clip "CLIP", model "MODEL", vae "VAE"} (node "CheckpointLoaderSimple" {:ckpt_name "juggernautXL_v9Rdphoto2Lightning.safetensors"})
        {negative_conditioning "CONDITIONING"} (node "CLIPTextEncode" {:clip clip :text "text, watermark"})
        {positive_conditioning "CONDITIONING"} (node "CLIPTextEncode" {:clip clip :text "beautiful scenery nature glass bottle landscape, purple galaxy bottle"})
        {empty_latent_image "LATENT"} (node "EmptyLatentImage" {:width 1024 :height 1024 :batch_size 1})
        {latent_image "LATENT"} (node "KSampler" {:seed 123 :control_after_generate "randomize" :steps 6 :cfg 2 :sampler_name "dpmpp_sde" :scheduler "karras" :denoise 1 :positive positive_conditioning :negative negative_conditioning :model model :latent_image empty_latent_image})
        {image "IMAGE"} (node "VAEDecode" {:samples latent_image :vae vae})]
    (output-node "SaveImage" {:images image})))

(defn to-comfy-ui-inputs [inputs]
  (->> inputs (map (fn [[k, v]]
                     (if (coll? v) {k (list (:node-id v) (:output-index v))} {k v})))
       (into {})))


;; (to-comfy-ui-inputs {:a {:node-id "kurac" :output-index 2} :b "blaz"})

(defn to-comfy-ui [node]
  {:id (:node-id node)
   :class_type (:node-type node)
   :_meta {:title (:node-type node)}
   :inputs (to-comfy-ui-inputs (:inputs node))})

(to-comfy-ui basic-workflow)


(defn process-node-tree [last-node]
  (flatten
   [(to-comfy-ui last-node)
    (map process-node-tree (filter map? (vals (:inputs last-node))))]))


(def final-workflow (reduce 
 (fn [acc entry]
   (if (some #(= % entry) acc) 
     acc (conj acc entry))) [] (process-node-tree basic-workflow)))


(spit "workflow-clojure.json" (json/write-str final-workflow))