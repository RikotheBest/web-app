package com.example.springboot_webapp.service;

import com.example.springboot_webapp.model.Image;
import com.example.springboot_webapp.model.Product;
import com.example.springboot_webapp.repo.ImageRepo;
import com.example.springboot_webapp.repo.Repo;
import org.springframework.beans.factory.annotation.Autowired;



import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;

import java.util.*;

@Service
public class ProductService {
    private Repo repo;
    private ImageRepo imageRepo;
    private RedisTemplate<String, Object> redisTemplate;
    private static final String PRODUCT_ZSET = "products";
    private static final String CATEGORY_ZSET = "products:category:";


    @Autowired
    public ProductService(ImageRepo imageRepo,Repo repo, RedisTemplate<String, Object> redisTemplate) {
        this.imageRepo = imageRepo;
        this.repo = repo;
        this.redisTemplate = redisTemplate;
    }

    public ProductService() {
    }

//    @Cacheable(value = "page", key = "#currentPage")
    public Page<Product> findAll(int currentPage, int pageSize){
        int start = currentPage * pageSize;
        int end = start + pageSize - 1;
        long totalProducts = redisTemplate.opsForZSet().size(PRODUCT_ZSET);

        if(totalProducts != 0){
            Set<Object> objects = redisTemplate.opsForZSet().range(PRODUCT_ZSET, start, end);
            List<Product> products = objects.stream()
                    .map(object -> (Product)object)
                    .toList();

            return new PageImpl<>(products, PageRequest.of(currentPage, pageSize), totalProducts);
        } else {
            List<Product> products = repo.findAll();
            products.forEach(p -> redisTemplate.opsForZSet().add(PRODUCT_ZSET, p, p.getId()));
            return repo.findAll(PageRequest.of(currentPage,pageSize));
        }
    }
    public List<Product> findAll() {
        return repo.findAll();
    }



    @Cacheable(value = "product", key = "#id")
    public Product findProduct(int id){

        return repo.findById(id).orElse(null);

    }


    @Transactional
    public void addProduct(Product product, MultipartFile multipartImage) throws IOException {
        Image image = new Image(multipartImage.getName(),
                                multipartImage.getContentType(),
                                multipartImage.getBytes(),
                                product);

        redisTemplate.getConnectionFactory().getConnection().execute("flushdb");

        imageRepo.save(image);
        repo.save(product);
    }


    public byte[] getImageById(int id) {
        String encodedImage = (String)redisTemplate.opsForValue().get("image::" + id);
        byte[] decodedImage;
        if(encodedImage != null){
            decodedImage = Base64.getDecoder().decode(encodedImage);
        }else {
            decodedImage = imageRepo.findByProductId(id).getImageData();
            redisTemplate.opsForValue().set("image::" + id, decodedImage);
        }

        return decodedImage;


    }

    @Transactional
    public void updateProduct(int id, Product product, MultipartFile multipartImage) throws IOException {
        Image image = imageRepo.findByProductId(id);
        if(image == null) image = new Image();
        image.setImageData(multipartImage.getBytes());
        image.setImageName(multipartImage.getName());
        image.setImageType(multipartImage.getContentType());
        image.setProduct(product);


        redisTemplate.getConnectionFactory().getConnection().execute("flushdb");
        repo.save(product);
        imageRepo.save(image);
    }




    @Transactional
    public void deleteProduct(int id) {
        repo.deleteById(id);
        imageRepo.deleteByProductId(id);
        redisTemplate.getConnectionFactory().getConnection().execute("flushdb");
    }

    public Page<Product> filter(String category, int currentPage, int pageSize) {
        int offset = currentPage * pageSize;
        int end = offset + pageSize - 1;
        long totalProducts = redisTemplate.opsForZSet().size(CATEGORY_ZSET + category.toLowerCase());

        if(totalProducts != 0){
            List<Product> products = redisTemplate.opsForZSet()
                     .range(CATEGORY_ZSET + category.toLowerCase(), offset, end)
                     .stream()
                     .map(object -> (Product) object)
                     .toList();
            return new PageImpl<>(products, PageRequest.of(currentPage, pageSize), totalProducts);
        } else{
            List<Product> filteredProducts = repo.findAllByCategory(category);
            filteredProducts.forEach(p -> redisTemplate.opsForZSet().add(CATEGORY_ZSET + category.toLowerCase(), p, p.getId()));
            return repo.findAllByCategory(category, PageRequest.of(currentPage, pageSize));
        }
    }


    public List<Product> searchFor(String keyword) {

        return repo.findAllByKeyword(keyword);

    }



}
