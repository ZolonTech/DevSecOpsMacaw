//
//  This file was auto-generated by Macaw tools 0.9.5-CR6-SNAPSHOT version built on Tue, 23 Jan 2018 14:35:13 +0530
//
package http.macaw.io.service.image.manager.rev180123.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cfx.im.dao.CustomerDAO;
import com.cfx.im.dao.ImageApprovalDAO;
import com.cfx.im.dao.ImageDAO;
import com.cfx.im.db.DBCluster;
import com.cfx.im.dto.Customer;
import com.cfx.im.dto.Image;
import com.cfx.im.dto.ImageApproval;
import com.cfx.im.utils.DBUtils;
import com.cfx.im.utils.ServiceConfiguration;
import com.cfx.im.utils.Transformer;
import com.cfx.service.api.ServiceException;
import http.macaw.io.service.image.manager.rev180123.Approval;
import http.macaw.io.service.image.manager.rev180123.ApprovalStatus;
import http.macaw.io.service.image.manager.rev180123.DomainEntityInstantiator;
import http.macaw.io.service.image.manager.rev180123.ImageManagerServiceException;

public class ImageManagerService implements com.cfx.service.api.Service, http.macaw.io.service.image.manager.rev180123.ImageManagerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageManagerService.class);

    @Override
    public void initialize(com.cfx.service.api.config.Configuration config) throws com.cfx.service.api.ServiceException {
        ServiceConfiguration.getInstance().init(config);
        try {
            DBCluster.getInstance().init(config);
        } catch (final Exception e) {
            throw new ServiceException("Unable to initialize DB connections. Exiting...", e);
        }
    }

    @Override
    public void start(com.cfx.service.api.StartContext startContext) throws com.cfx.service.api.ServiceException {
    }

    @Override
    public void stop(com.cfx.service.api.StopContext stopContext) throws com.cfx.service.api.ServiceException {
        DBCluster.getInstance().teardown();
    }

    @Override
    public http.macaw.io.service.image.manager.rev180123.Customer createCustomer(http.macaw.io.service.image.manager.rev180123.Customer customer)  throws http.macaw.io.service.image.manager.rev180123.ImageManagerServiceException {
        if (customer == null) {
            throw new ImageManagerServiceException("Customer cant be null");
        }

        Customer dto = new Customer();
        dto.setAddress(customer.getAddress());
        dto.setCustomerId(customer.getId());
        dto.setDescription(customer.getDescription());
        dto.setDOB(customer.getDob());
        dto.setName(customer.getName());
        dto.setEmailId(customer.getEmailId());

        // Insert customer in DB
        new CustomerDAO().insert(dto);

        return customer;
    }

    @Override
    public java.util.List<http.macaw.io.service.image.manager.rev180123.Approval> getApprovals(
            http.macaw.io.service.image.manager.rev180123.ApprovalStatus status, String customerId)
                    throws http.macaw.io.service.image.manager.rev180123.ImageManagerServiceException {
        String query = "SELECT * FROM imageapproval ";
        String statusCondition = null;
        if (status != null && !ApprovalStatus.ALL.equals(status)) {
            statusCondition = " status = '" + status.name() + "'";
        }
        String customerIdCondtition = null;
        if (customerId != null) {
            customerIdCondtition = " customerid = '" + customerId + "'";
        }

        if (statusCondition != null && customerIdCondtition != null) {
            query = query + " WHERE " + customerIdCondtition + " AND " + statusCondition;
        } else if (statusCondition != null) {
            query = query + " WHERE " + statusCondition;
        } else if (customerIdCondtition != null) {
            query = query + " WHERE " + customerIdCondtition;
        }

        LOGGER.info("Getting approvals for query: " + query);

        List<ImageApproval> approvals = new ImageApprovalDAO().findByQuery(query);
        return transform(approvals);
    }

    @Override
    public void updateApproval(String customerId, String imageId, ApprovalStatus status, String reason) throws ImageManagerServiceException {
        validateCustomer(customerId);
        validateImage(imageId);

        // Create image approval dto
        ImageApproval dto = new ImageApproval();
        dto.setCustomerId(customerId);
        dto.setImageId(UUID.fromString(imageId));
        dto.setReason(reason);
        dto.setStatus(status.name());
        dto.setUpdateAt(new Date().getTime());
        ImageApprovalDAO dao = new ImageApprovalDAO();
        dao.upsert(dto);
    }

    @Override
    public http.macaw.io.service.image.manager.rev180123.Image uploadImage(http.macaw.io.service.image.manager.rev180123.Image image, http.macaw.io.service.image.manager.rev180123.SourceInfo sourceInfo) throws http.macaw.io.service.image.manager.rev180123.ImageManagerServiceException {
        validateCustomer(image.getCustomerId());

        // Read image as binary object
        // final ByteBuffer bb = ImageReader.readImageFromMinio(sourceInfo.getUrl());

        // Create image dto
        Image dto = new Image();
        dto.setId(UUID.randomUUID());
        dto.setCreatedDate(new Date());
        dto.setName(image.getName());
        // dto.setObject(bb);
        dto.setCustomerId(image.getCustomerId());
        dto.setDescription(image.getDescription());
        dto.setUrl(image.getUrl());
        // Insert image object in db.
        ImageDAO dao = new ImageDAO();
        dao.insert(dto);

        // Create image approval
        DBUtils.createPendingApproval(dto);
        image.setCreatedDate(dto.getCreatedDate().getTime());
        image.setId(dto.getId().toString());
        return image;
    }

    @Override
    public List<http.macaw.io.service.image.manager.rev180123.Customer> getCustomers() throws ImageManagerServiceException {
        List<Customer> dtos = new CustomerDAO().findAll();
        List<http.macaw.io.service.image.manager.rev180123.Customer> customers = new ArrayList<>();
        if (dtos == null) {
            return customers;
        }
        for (Customer dto : dtos) {
            customers.add(Transformer.dtoToEntity(dto));
        }
        return customers;
    }

    // Private methods
    private List<Approval> transform(List<ImageApproval> dtos) throws ImageManagerServiceException {
        List<Approval> approvals = new ArrayList<>();
        if (dtos == null) {
            return approvals;
        }
        for (ImageApproval dto : dtos) {
            http.macaw.io.service.image.manager.rev180123.Image image = getImage(dto.getImageId());
            http.macaw.io.service.image.manager.rev180123.Customer customer = getCustomer(dto.getCustomerId());

            Approval approval = DomainEntityInstantiator.getInstance().newInstance(Approval.class);
            approval.setImage(image);
            approval.setCustomer(customer);
            approval.setReason(dto.getReason());
            approval.setStatus(ApprovalStatus.from(dto.getStatus()));
            approvals.add(approval);
        }

        return approvals;
    }

    private http.macaw.io.service.image.manager.rev180123.Image getImage(UUID imageId) throws ImageManagerServiceException {
        Image dto = DBUtils.getImage(imageId);

        http.macaw.io.service.image.manager.rev180123.Image image = Transformer.dtoToEntity(dto);

        // Dont set image binary data.
        // ByteBuffer bb = dto.getObject();
        // byte[] byteArray = new byte[bb.remaining()];
        // bb.get(byteArray);
        // image.setObject(byteArray);

        return image;
    }

    private http.macaw.io.service.image.manager.rev180123.Customer getCustomer(String id) throws ImageManagerServiceException {
        Customer dto = DBUtils.getCustomer(id);
        return Transformer.dtoToEntity(dto);
    }

    private void validateImage(String id) throws ImageManagerServiceException {
        DBUtils.getImage(UUID.fromString(id));
    }

    private void validateCustomer(String customerUniqueCode) throws ImageManagerServiceException {
        DBUtils.getCustomer(customerUniqueCode);
    }
}