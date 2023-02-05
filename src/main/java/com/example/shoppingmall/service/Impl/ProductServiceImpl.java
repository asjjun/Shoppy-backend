package com.example.shoppingmall.service.Impl;

import com.example.shoppingmall.data.dto.queryselect.ChangeStockQuery;
import com.example.shoppingmall.data.dto.queryselect.QueryOrderProduct;
import com.example.shoppingmall.data.dto.queryselect.SelectProductStockQuery;
import com.example.shoppingmall.data.dto.request.RequestOrder;
import com.example.shoppingmall.data.dto.request.RequestProduct;
import com.example.shoppingmall.data.dto.request.RequestProductModify;
import com.example.shoppingmall.data.dto.response.*;
import com.example.shoppingmall.data.entity.Banner;
import com.example.shoppingmall.data.entity.Product;
import com.example.shoppingmall.data.entity.User;
import com.example.shoppingmall.repository.banner.BannerRepository;
import com.example.shoppingmall.repository.product.ProductRepository;
import com.example.shoppingmall.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

@Service
public class ProductServiceImpl implements ProductService {

    @PersistenceContext
    private EntityManager entityManager;

    private final ProductRepository productRepository;
    private final BannerRepository bannerRepository;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository, BannerRepository bannerRepository){
        this.productRepository = productRepository;
        this.bannerRepository = bannerRepository;
    }

    @Override
    public List<List<?>>  mainPageProductList() {
        List<Product> productList = productRepository.findTop8ByOrderByIdDesc();
        List<Banner> bannerList = bannerRepository.findAll();

        return entityToDtoMainPageList(productList, bannerList);
    }

    @Override
    public List<ResponseProductSummary> findByProductName(String keyword, String sort) {
        List<Product> productList = new ArrayList<>();
        switch (sort) {
            case "hits" -> productList = productRepository.findByNameContainingOrderByHitsDesc(keyword);
            case "date" -> productList = productRepository.findByNameContainingOrderByDateDesc(keyword);
            case "favorite"-> productList = productRepository.findByNameContainingOrderByFavoriteDesc(keyword);
            case "purchase" -> {
                return purchaseSort(productRepository.findSearchProductPurchase(keyword));
            }
        }

        return entityToDtoResponseProductSummary(productList);
    }

    @Override
    public List<ResponseProductSummary> findAllProduct(String sort) {
        List<Product> productList = new ArrayList<>();
        switch (sort) {
            case "hits" -> productList = productRepository.findAllByOrderByHitsDesc();
            case "date" -> productList = productRepository.findAllByOrderByDateDesc();
            case "favorite"-> productList = productRepository.findAllByOrderByFavoriteDesc();
            case "purchase" -> {
                return purchaseSort(productRepository.findAllProductPurchase());
            }
        }

        return entityToDtoResponseProductSummary(productList);
    }

    @Override
    public List<ResponseProductSummary> findByCategory(String category, String sort) {
        List<Product> productList = new ArrayList<>();
        switch (sort) {
            case "hits" -> productList = productRepository.findByCategoryOrderByHitsDesc(category);
            case "date" -> productList = productRepository.findByCategoryOrderByDateDesc(category);
            case "favorite"-> productList = productRepository.findByCategoryOrderByFavoriteDesc(category);
            case "purchase" -> {
                return purchaseSort(productRepository.findCategoryProductPurchase(category));
            }
        }

        return entityToDtoResponseProductSummary(productList);
    }

    @Override
    public ResponseProduct findById(Long id) {
        Product product = productRepository.findById(id).orElse(null);
        if (product == null){
            return null;
        }

        return ResponseProduct.builder().product(product).build();
    }

    @Override
    public List<ResponseProductSummary> findByUsername(Long userId) {
        List<Product> productList = productRepository.findByUserId(userId);

        return entityToDtoResponseProductSummary(productList);
    }

    @Override
    public boolean CreateProduct(RequestProduct requestProduct, User user){
        Product product = requestProduct.toEntity(user);

        Product createdProduct = productRepository.save(product);

        return !createdProduct.getName().isEmpty();
    }

    @Override
    @Transactional
    public ResponseProduct editProduct(Long id, User user) {
        Product product = productRepository.findById(id).orElse(null);
        if(product == null) {
            return null;
        }

        // product 를 등록한 유저아이디와 받은 jwt 의 유저 아이디가 같은지 확인
        if (product.getUser().getUsername().equals(user.getUsername())) {
            return ResponseProduct.builder().product(product).build();
        } else {
            return null;
        }
    }

    @Override
    @Transactional
    public ResponseProduct updateProduct(RequestProductModify requestProductModify) {
        Product product = productRepository.findById(requestProductModify.getId()).orElse(null);
        if(product == null) {
            return null;
        }

        // product 를 등록한 유저아이디와 받은 jwt 의 유저 아이디가 같은지 확인
        if(product.getUser().getUsername().equals(requestProductModify.getUsername())){
            requestProductModify.toEntity(product);
            Product modified_Product = productRepository.save(product);

            return ResponseProduct.builder().product(modified_Product).build();
        }else {
            return null;
        }
    }

    @Override
    @Transactional
    public boolean deleteProduct(Long id, User user) {
        Product product = productRepository.findById(id).orElse(null);
        if(product == null) {
            return false;
        }
        System.out.println(product.getUser());
        // product 를 등록한 유저아이디와 받은 jwt 의 유저 아이디가 같은지 확인
        if (product.getUser().getUsername().equals(user.getUsername())) {
            productRepository.deleteProductID(product.getId());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void increaseHits(Long id) {
        productRepository.increaseHits(id);
    }

    @Override
    public void increaseFavorite(Long id) {
        productRepository.increaseFavorite(id);
    }

    @Override
    public void decreaseFavorite(Long id) {
        productRepository.decreaseFavorite(id);
    }

    @Override
    public Boolean purchaseProduct(RequestOrder requestOrder) {
        int value = 0;
        List<ChangeStockQuery> changeStockList =
                productRepository.findRemoveByProductIDList(requestOrder.getQueryOrderProductList().stream().map(QueryOrderProduct::getProduct_id).toList());

        HashMap<Long, Integer> productMap = new HashMap<>();
        for(ChangeStockQuery changeStockQuery : changeStockList){
            productMap.put(changeStockQuery.getId(), changeStockQuery.getStock());
        }

        for(QueryOrderProduct queryOrderProduct : requestOrder.getQueryOrderProductList()){
            value = productMap.get(queryOrderProduct.getProduct_id()) - queryOrderProduct.getCount();

            if(value < 0) {
                return false;
            }

            productMap.put(queryOrderProduct.getProduct_id(), value);
        }


        return productRepository.updateProductListStock(productMap) != 0;
    }

    @Override
    @Transactional
    public Boolean stockUpProduct(User user, List<ChangeStockQuery> changeStockQueryList) {
        Long register_id = user.getId();
        int value = 0;

        List<SelectProductStockQuery> selectProductStockQueryList =
                productRepository.findAddStockByProductIDList(changeStockQueryList.stream().map(ChangeStockQuery::getId).toList());

        HashMap<Long, Integer> productMap = new HashMap<>();
        for(SelectProductStockQuery selectProductStockQuery : selectProductStockQueryList) {
            if(selectProductStockQuery.getUser_id() != register_id){
                return false;
            }
            productMap.put(selectProductStockQuery.getProduct_id(), selectProductStockQuery.getStock());
        }

        for(ChangeStockQuery changeStockQuery : changeStockQueryList){
            value = productMap.get(changeStockQuery.getId()) + changeStockQuery.getStock();

            if(value < 0) {
                System.out.println("false2");
                return false;
            }

            productMap.put(changeStockQuery.getId(), value);
        }

        return productRepository.updateProductListStock(productMap) != 0;
    }

    public List<ResponseProductSummary> purchaseSort(List<ResponseProductPurchase> productPurchaseList) {
        Collections.sort(productPurchaseList);

        return productPurchaseList.stream().map(productPurchase -> ResponseProductSummary
                .dtoBuilder()
                .responseProductPurchase(productPurchase)
                .dtoBuild()).toList();
    }

    /** Entity to Dto */
    public List<ResponseProductSummary> entityToDtoResponseProductSummary(List<Product> productList) {
        List<ResponseProductSummary> responseProductList = new ArrayList<>();
        if (!productList.isEmpty()){
            for(Product product : productList){
                responseProductList.add(ResponseProductSummary.builder().product(product).build());
            }
        }

        return responseProductList;
    }

    /** Entity to Dto */
    public List<List<?>> entityToDtoMainPageList(List<Product> productList, List<Banner> bannerList) {
        List<ResponseProductMain> responseProductList = new ArrayList<>();
        List<ResponseBanner> responseBannerList = new ArrayList<>();
        List<List<?>> returnList = new ArrayList<>();

        if (!productList.isEmpty() && !bannerList.isEmpty()) {
            for(Product product : productList){
                responseProductList.add(ResponseProductMain.builder().product(product).build());
            }
            for (Banner banner : bannerList) {
                responseBannerList.add(ResponseBanner.builder().banner(banner).build());
            }
            returnList.add(responseProductList);
            returnList.add(responseBannerList);
        }

        return returnList;
    }
}
