(ns get-started.core 
  (:require [clojure.data.json :as json]))

(defn node-keyword [name] (keyword  name))

(def json-data
  (-> "object_info.json"
      slurp
      (json/read-str :key-fn keyword)))

(defn unique-node-name [node-type node-inputs]
  (str node-type (hash (list node-type node-inputs))))

(defn node [node-type node-inputs]
  (->> (:output ((node-keyword node-type) json-data))
       (map-indexed (fn [index type] (hash-map type {:output-index index :node-type node-type :node-id (unique-node-name node-type node-inputs) :inputs node-inputs})))
       (reduce merge)))

(defn output-node [type node-inputs]
  {:node-type type :node-id (unique-node-name type 0) :inputs node-inputs})

(defn to-comfy-ui-inputs [inputs]
  (->> inputs (map (fn [[k, v]]
                     (if (coll? v) {k (list (:node-id v) (:output-index v))} {k v})))
       (into {})))

(defn to-comfy-ui [node]
  {:id (:node-id node)
   :class_type (:node-type node)
   :_meta {:title (:node-id node)}
   :inputs (to-comfy-ui-inputs (:inputs node))})

(defn process-node-tree [last-node]
  (flatten
   [(to-comfy-ui last-node)
    (map process-node-tree (filter map? (vals (:inputs last-node))))]))

(defn produce-final-workflow [workflow]
  (->> (process-node-tree workflow)
       (map (fn [entry] {(:id entry) entry}))
       (into {})))

(def basic-workflow
  (let [{clip "CLIP", model "MODEL", vae "VAE"} (node "CheckpointLoaderSimple" {:ckpt_name "juggernautXL_v9Rdphoto2Lightning.safetensors"})
        {negative_conditioning "CONDITIONING"} (node "CLIPTextEncode" {:clip clip :text "text, watermark"})
        {positive_conditioning "CONDITIONING"} (node "CLIPTextEncode" {:clip clip :text "beautiful scenery nature glass bottle landscape, purple galaxy bottle"})
        {empty_latent_image "LATENT"} (node "EmptyLatentImage" {:width 1024 :height 1024 :batch_size 1})
        {latent_image "LATENT"} (node "KSampler" {:seed 123 :control_after_generate "randomize" :steps 6 :cfg 2 :sampler_name "dpmpp_sde" :scheduler "karras" :denoise 1 :positive positive_conditioning :negative negative_conditioning :model model :latent_image empty_latent_image})
        {image "IMAGE"} (node "VAEDecode" {:samples latent_image :vae vae})]
    (output-node "SaveImage" {:images image})))

(spit "workflow-clojure.json" (json/write-str (produce-final-workflow basic-workflow)))