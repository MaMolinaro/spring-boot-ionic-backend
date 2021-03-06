package com.molinaro.springbootionicbackend.services;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.molinaro.springbootionicbackend.domain.Cidade;
import com.molinaro.springbootionicbackend.domain.Cliente;
import com.molinaro.springbootionicbackend.domain.Endereco;
import com.molinaro.springbootionicbackend.domain.enums.Perfil;
import com.molinaro.springbootionicbackend.domain.enums.TipoCliente;
import com.molinaro.springbootionicbackend.dto.ClienteDTO;
import com.molinaro.springbootionicbackend.dto.ClienteNewDTO;
import com.molinaro.springbootionicbackend.repositories.CidadeRepository;
import com.molinaro.springbootionicbackend.repositories.ClienteRepository;
import com.molinaro.springbootionicbackend.repositories.EnderecoRepository;
import com.molinaro.springbootionicbackend.security.UserSpringSecurity;
import com.molinaro.springbootionicbackend.services.exceptions.AuthorizationException;
import com.molinaro.springbootionicbackend.services.exceptions.DataIntegrityException;
import com.molinaro.springbootionicbackend.services.exceptions.ObjectNotFoundException;

@Service
public class ClienteService {
	
	@Autowired
	private BCryptPasswordEncoder bCryptPasswordEncoder;
	
	@Autowired
	private ClienteRepository clienteRepository;
	
	@Autowired
	private CidadeRepository cidadeRepository;

	@Autowired
	private EnderecoRepository enderecoRepository;
	
	@Autowired
	private S3Service s3service;
	
	@Autowired
	private ImageService imageService;
	
	@Value("${img.prefix.client.profile}")
	private String prefix;
	
	@Value("${img.profile.size}")
	private Integer size; 

	public Cliente find(Integer id) {
		
		UserSpringSecurity user = UserService.authenticated();
		
		if (user == null || !user.hasRole(Perfil.ADMIN) && !id.equals(user.getId())) {
			throw new AuthorizationException("Acesso Negado");
		}
		
		Cliente obj = clienteRepository.findOne(id);
		
		if (obj == null) {
			throw new ObjectNotFoundException("Não existe Cliente com id: #" + id);
		}
		
		return obj;
	}
	
	public Cliente insert(Cliente obj) {
		obj.setId(null);
		obj = clienteRepository.save(obj);
		enderecoRepository.save(obj.getEnderecos());
		return obj;
	}
	
	public Cliente update(Cliente obj) {
		Cliente newObj = find(obj.getId());
		updateData(newObj, obj);
		return clienteRepository.save(newObj);
	}
	
	public void delete(Integer id) {
		find(id);
		try {
			clienteRepository.delete(id);			
		}
		catch (DataIntegrityViolationException e) {
			throw new DataIntegrityException("Não é possível excluir Cliente com Pedidos associados!");
		}
	}
	
	public List<Cliente> findAll() {
		return clienteRepository.findAll();
	}
	
	public Cliente findByEmail(String email) {
		UserSpringSecurity user = UserService.authenticated();
		
		if (user == null || !user.hasRole(Perfil.ADMIN) && !email.equals(user.getUsername())) {
			throw new AuthorizationException("Acesso Negado");
		}
		
		Cliente obj = clienteRepository.findOne(user.getId());
		
		if (obj == null) {
			throw new ObjectNotFoundException("Não existe Cliente com id: #" + user.getId());
		}
		
		return obj;		
	}
	
	public Page<Cliente> findPage(Integer page, Integer linesPerPage, String orderBy, String direction) {
		PageRequest pageRequest = new PageRequest(page, linesPerPage, Direction.valueOf(direction), orderBy);
		return clienteRepository.findAll(pageRequest);
	}
	
	public Cliente fromDTO(ClienteDTO objDto) {
		return new Cliente(objDto.getId(), objDto.getNome(), objDto.getEmail(), null, null, null);
	}
	
	public Cliente fromDTO(ClienteNewDTO objDto) {
		Cliente cli =  new Cliente(
				null, 
				objDto.getNome(), 
				objDto.getEmail(), 
				objDto.getCpfOrCnpj(), 
				TipoCliente.toEnum(objDto.getTipoCliente()),
				bCryptPasswordEncoder.encode(objDto.getSenha()));
		
		Cidade cid = cidadeRepository.findOne(objDto.getCidadeId());
		
		Endereco end = new Endereco(
				null, 
				objDto.getLogradouro(), 
				objDto.getNumero(), 
				objDto.getComplemento(),
				objDto.getBairro(),
				objDto.getCep(),
				cli,
				cid);
		
		cli.getEnderecos().add(end);
		
		cli.getTelefones().add(objDto.getTelefone1());
		
		if (objDto.getTelefone2() != null) {
			cli.getTelefones().add(objDto.getTelefone2());
		}
		
		if (objDto.getTelefone3() != null) {
			cli.getTelefones().add(objDto.getTelefone3());
		}
		
		return cli;
	}	
	
	private void updateData(Cliente newObj, Cliente obj) {
		newObj.setNome(obj.getNome());
		newObj.setEmail(obj.getEmail());
	}
	
	public URI uploadProfilePicture(MultipartFile multipartFile) {
		UserSpringSecurity user = UserService.authenticated();
		if (user == null) {
			throw new AuthorizationException("Acesso Negado!!");
		}
		
		BufferedImage jpgImage = imageService.getJpgImageFromFile(multipartFile);
		
		jpgImage = imageService.cropSquare(jpgImage);
		jpgImage = imageService.resize(jpgImage, size);
		
		String fileName = prefix + user.getId() + ".jpg";
		
		URI uri = s3service.uploadFile(imageService.getInputStream(jpgImage, "jpg"), fileName, "image");
		
		Cliente cliente = clienteRepository.findOne(user.getId());
		cliente.setImageUrl(uri.toString());
		clienteRepository.save(cliente);
		
		return uri;
	}
}
