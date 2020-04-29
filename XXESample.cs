public void Post([RawRequestDataAttribute] string xml)
{
	using(StringReader textReader = new StringReader(xml))
	{
		XmlReaderSettings settings = new XmlReaderSettings();
		settings.DtdProcessing = DtdProcessing.Parse;
		settings.XmlResolver = new XmlResolver();

		XmlReader reader = XmlReader.Create(textReader,settings);
		XmlSerializer serializer = new XmlSeializer(typeof(OrderModel));

		var orderModel = serializer.Deserialize(reader) as OrderModel;
		orderRepository.AddOrder(orderModel)
	}
}