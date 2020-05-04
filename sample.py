import yaml
import xmltodict
from xml.dom import pulldom
from xml.sax import make_parser, SAXException
from xml.sax.handler import feature_external_ges

from flask import jsonify, request
from app.models import Product, Order, Item
from app.api import bp
from app.api.auth import token_required, jwt_decode_handler
from app.api.errors import error_response


@bp.route('/products', methods=['GET'])
@token_required
def products_api():
    return jsonify([
        {
            'id': product.id,
            'name': product.name,
            'price': product.price,
            'description': product.description,
            'image': product.image,
            'stock': product.stock
        } for product in Product.query.limit(100).all()])


@bp.route('/products-search', methods=['POST'])
@token_required
def products_search_api():

    try:
        query = request.get_json().get('query')
    except AttributeError:
        return error_response(400)

    return jsonify([
        {
            'id': product.id,
            'name': product.name,
            'price': product.price,
            'description': product.description,
            'image': product.image,
            'stock': product.stock
        } for product in Product.query.filter(
            (Product.name.contains(query)) |
            (Product.description.contains(query))
        ).limit(100).all()])


@bp.route('/products-search-yaml', methods=['POST'])
@token_required
def products_search_yaml_api():

    try:
        query = yaml.safe_load(request.data).get('query')
    except AttributeError:
        return error_response(400)

    return jsonify([
        {
            'id': product.id,
            'name': product.name,
            'price': product.price,
            'description': product.description,
            'image': product.image,
            'stock': product.stock
        } for product in Product.query.filter(
            (Product.name.contains(query)) |
            (Product.description.contains(query))
        ).limit(100).all()])


@bp.route('/products-search-xml', methods=['POST'])
@token_required
def products_search_xml_api():

    parser = make_parser()
    parser.setFeature(feature_external_ges, True)
    try:
        document = pulldom.parseString(request.data.decode(), parser=parser)
        str_xml = ''
        for event, node in document:
            if event == pulldom.START_ELEMENT:
                exp = document.expandNode(node)
                if exp:
                    str_xml += exp
                str_xml += node.toxml()
        data = xmltodict.parse(str_xml)
        query = data.get('search').get('query')
    except (SAXException, ValueError) as e:
        return error_response(400, 'XML parse error - %s' % e)
    except Exception as e:
        return error_response(400, e)

    try:
        return jsonify([
            {
                'id': product.id,
                'name': product.name,
                'price': product.price,
                'description': product.description,
                'image': product.image,
                'stock': product.stock
            } for product in Product.query.filter(
                (Product.name.contains(query)) |
                (Product.description.contains(query))
            ).limit(100).all()])
    except Exception as e:
        return error_response(400, 'Malformed Query %s' % query)


@bp.route('/purchase-history', methods=['GET'])
@token_required
def purchase_history_api():
    payload = jwt_decode_handler(
        request.headers.get('Authorization').split()[1])
    return jsonify([
        {
            'items': [
                {
                    'id': item.id,
                    'name': item.name,
                    'quantity': item.quantity,
                    'price': item.price
                }
                for item in Item.query.filter_by(order_id=order.id).all()
            ],
            'id': order.id,
            'date': order.date,
            'payment': order.payment_data,
        } for order in Order.query.filter_by(
            user_id=payload.get('user_id')).limit(100)])
